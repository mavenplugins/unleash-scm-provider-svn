package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;
import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.annotations.ScmProviderType;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNStatusTypeMapEntryFilter;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteTagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.TagRequest;

@ScmProviderType("svn")
public class ScmProviderSVN implements ScmProvider {
  private SVNClientManager clientManager;
  private File workingDir;

  @Override
  public void initialize(File workingDirectory, Optional<String> username, Optional<String> password) {
    this.workingDir = workingDirectory;
    if (username.isPresent()) {
      this.clientManager = SVNClientManager.newInstance(null, username.get(), password.get());
    } else {
      this.clientManager = SVNClientManager.newInstance();
    }
  }

  @Override
  public void close() {
    this.clientManager.dispose();
  }

  @Override
  public String commit(CommitRequest request) throws ScmException {
    try {
      Multimap<SVNStatusType, File> stati = getFileStatiFromWorkingCopy(request.getPathsToCommit().orNull());

      // first step: add all unversioned files
      Collection<File> unversionedFiles = Multimaps.filterEntries(stati,
          new SVNStatusTypeMapEntryFilter(SVNStatusType.STATUS_UNVERSIONED, SVNStatusType.STATUS_NONE)).values();
      if (!unversionedFiles.isEmpty()) {
        SVNWCClient wcClient = this.clientManager.getWCClient();
        wcClient.doAdd(unversionedFiles.toArray(new File[unversionedFiles.size()]), true, false, true,
            SVNDepth.INFINITY, true, false, true);
      }

      // second step: get all modified and added files as well as all unversioned ones (are now added after step one)
      // then commit these files!
      Collection<File> filesToCommit = Multimaps
          .filterEntries(stati, new SVNStatusTypeMapEntryFilter(SVNStatusType.STATUS_MODIFIED,
              SVNStatusType.STATUS_ADDED, SVNStatusType.STATUS_UNVERSIONED, SVNStatusType.STATUS_NONE))
          .values();
      if (!filesToCommit.isEmpty()) {
        SVNCommitClient commitClient = this.clientManager.getCommitClient();
        SVNCommitInfo info = commitClient.doCommit(filesToCommit.toArray(new File[filesToCommit.size()]), true,
            request.getMessage(), null, null, true, true, SVNDepth.INFINITY);
        return String.valueOf(info.getNewRevision());
      }
      return getLocalRevision();
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.COMMIT, "Could not commit changes to the remote SVN repository.", e);
    }
  }

  @Override
  public void push() throws ScmException {
    // Nothing to do here since all modifying svn operations are remote by nature!
  }

  @Override
  public String update() throws ScmException {
    // TODO implement error handling and merging
    try {
      SVNUpdateClient updateClient = this.clientManager.getUpdateClient();
      updateClient.setUpdateLocksOnDemand(true);
      long newRevision = updateClient.doUpdate(this.workingDir, SVNRevision.HEAD, SVNDepth.INFINITY, true, false);
      return String.valueOf(newRevision);
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.UPDATE,
          "Could not update local working copy with changes from remote SVN repository.", e);
    }
  }

  @Override
  public String tag(TagRequest request) throws ScmException {
    // TODO check for updates before tagging!
    String tagRevision = getLocalRevision();
    if (request.commitBeforeTagging()) {
      CommitRequest cr = CommitRequest.builder()
          .setMessage("Commit in preparation of tag creation. Tag name: " + request.getTagName()).build();
      tagRevision = commit(cr);
    } else {
      // TODO tag working copy!
    }

    try {
      String currentUrl = getCurrentConnectionUrl();
      SVNURL sourceUrl = SVNURL.parseURIEncoded(currentUrl);
      SVNCopySource source = new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, sourceUrl);

      String tagUrl = calculateTagConnectionString(currentUrl, request.getTagName());
      SVNURL destinationUrl = SVNURL.parseURIEncoded(tagUrl);

      SVNCopyClient copyClient = this.clientManager.getCopyClient();
      SVNCommitInfo info = copyClient.doCopy(new SVNCopySource[] { source }, destinationUrl, false, true, true,
          request.getMessage(), null);
      return String.valueOf(info.getNewRevision());
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.TAG, "An error occurred during SVN tag creation.", e);
    }
  }

  @Override
  public boolean hasTag(final String tagName) {
    SVNLogClient logClient = this.clientManager.getLogClient();
    String tagsUrl = getBaseTagsUrl(null);
    try {
      SVNURL svnUrl = SVNURL.parseURIEncoded(tagsUrl);
      DirEntryNameChecker nameChecker = new DirEntryNameChecker(tagName);
      logClient.doList(svnUrl, SVNRevision.HEAD, SVNRevision.HEAD, false, false, nameChecker);
      return nameChecker.isPresent();
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.TAG,
          "An error occurred while querying the remote SVN repository for tag '" + tagName + "'.", e);
    }
  }

  @Override
  public String deleteTag(DeleteTagRequest request) throws ScmException {
    SVNCommitClient commitClient = this.clientManager.getCommitClient();
    String tagUrl = calculateTagConnectionString(null, request.getTagName());
    try {
      SVNCommitInfo info = commitClient.doDelete(new SVNURL[] { SVNURL.parseURIEncoded(tagUrl) }, request.getMessage());
      return String.valueOf(info.getNewRevision());
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.TAG,
          "An error occurred during the deletion of the SVN tag '" + request.getTagName() + "'. SVN url: " + tagUrl, e);
    }
  }

  @Override
  public String getLocalRevision() {
    try {
      SVNStatus status = this.clientManager.getStatusClient().doStatus(this.workingDir, false);
      return String.valueOf(status != null ? status.getRevision().getNumber() : -1);
    } catch (SVNException e) {
      throw new IllegalStateException("Could not retrieve local SVN revision", e);
    }
  }

  @Override
  public String getLatestRemoteRevision() {
    try {
      SVNStatus status = this.clientManager.getStatusClient().doStatus(this.workingDir, true);
      return String.valueOf(status != null ? status.getRevision().getNumber() : -1);
    } catch (SVNException e) {
      throw new IllegalStateException("Could not retrieve remote SVN revision", e);
    }
  }

  @Override
  public String calculateTagConnectionString(String currentConnectionString, String tagName) {
    return getBaseTagsUrl(currentConnectionString) + "/" + tagName;
  }

  @Override
  public String calculateBranchConnectionString(String currentConnectionString, String branchName) {
    return getBaseBranchesUrl(currentConnectionString) + "/" + branchName;
  }

  @Override
  public boolean isTagInfoIncludedInConnection() {
    return true;
  }

  private Multimap<SVNStatusType, File> getFileStatiFromWorkingCopy(final Set<String> onlyIncludeRelativePaths) {
    final Multimap<SVNStatusType, File> stati = HashMultimap.create();
    try {
      this.clientManager.getStatusClient().doStatus(this.workingDir, SVNRevision.WORKING, SVNDepth.INFINITY, false,
          true, false, false, new ISVNStatusHandler() {
            @Override
            public void handleStatus(SVNStatus status) throws SVNException {
              File file = status.getFile();
              boolean include = true;

              if (onlyIncludeRelativePaths != null) {
                String relativePath = ScmProviderSVN.this.workingDir.toURI().relativize(file.toURI()).toString();
                if (relativePath.endsWith("/")) {
                  relativePath = relativePath.substring(0, relativePath.length() - 1);
                }
                include = onlyIncludeRelativePaths.contains(relativePath);
              }

              if (include) {
                stati.put(status.getContentsStatus(), file);
              }
            }
          }, null);
      return stati;
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.STATUS, "Unable to determine status of local SVN working copy.", e);
    }
  }

  private String getCurrentConnectionUrl() throws ScmException {
    SVNWCClient wcClient = this.clientManager.getWCClient();
    try {
      SVNInfo info = wcClient.doInfo(this.workingDir, SVNRevision.WORKING);
      return info.getURL().toString();
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.INFO,
          "An error occurred while retrieving the SVN URL of the current working copy.", e);
    }
  }

  private String getBaseUrl(String currentConnectionString) throws ScmException {
    String currentUrl = currentConnectionString != null ? currentConnectionString : getCurrentConnectionUrl();
    int trunkPos = currentUrl.indexOf("/trunk");
    int branchesPos = currentUrl.indexOf("/branches");
    int tagsPos = currentUrl.indexOf("/tags");
    if (trunkPos > -1) {
      return currentUrl.substring(0, trunkPos);
    } else if (branchesPos > -1) {
      return currentUrl.substring(0, branchesPos);
    } else if (tagsPos > -1) {
      return currentUrl.substring(0, tagsPos);
    }
    return currentUrl;
  }

  private String getBaseTagsUrl(String currentConnectionString) throws ScmException {
    return getBaseUrl(currentConnectionString) + "/tags";
  }

  private String getBaseBranchesUrl(String currentConnectionString) throws ScmException {
    return getBaseUrl(currentConnectionString) + "/branches";
  }

  private static class DirEntryNameChecker implements ISVNDirEntryHandler {
    private String entryname;
    private boolean isPresent;

    public DirEntryNameChecker(String entryname) {
      this.entryname = entryname;
    }

    @Override
    public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
      if (Objects.equal(dirEntry.getName(), this.entryname)) {
        this.isPresent = true;
      }
    }

    public boolean isPresent() {
      return this.isPresent;
    }
  }
}

package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;
import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.annotations.ScmProviderType;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNStatusTypeMapEntryFilter;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;
import com.itemis.maven.plugins.unleash.scm.requests.TagRequest;

@ScmProviderType("svn")
public class ScmProviderSVN implements ScmProvider {
  private SVNClientManager clientManager;
  private File workingDir;
  private SVNURL repositoryURL;

  @Override
  public void initialize(File workingDirectory) {
    this.workingDir = workingDirectory;
    this.clientManager = SVNClientManager.newInstance();
    this.repositoryURL = getRepositoryUrl();
  }

  private SVNURL getRepositoryUrl() {
    SVNWCClient client = this.clientManager.getWCClient();
    try {
      SVNInfo info = client.doInfo(this.workingDir, SVNRevision.WORKING);
      return info.getURL();
    } catch (SVNException e) {
      throw new RuntimeException("Unable to determine the SVN repository URL from local working copy.", e);
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
      Collection<File> unversionedFiles = Multimaps
          .filterEntries(stati, new SVNStatusTypeMapEntryFilter(SVNStatusType.STATUS_UNVERSIONED)).values();
      if (!unversionedFiles.isEmpty()) {
        SVNWCClient wcClient = this.clientManager.getWCClient();
        wcClient.doAdd(unversionedFiles.toArray(new File[unversionedFiles.size()]), true, false, true,
            SVNDepth.INFINITY, true, false, true);
      }

      // second step: get all modified and added files as well as all unversioned ones (are now added after step one)
      // then commit these files!
      Collection<File> filesToCommit = Multimaps
          .filterEntries(stati, new SVNStatusTypeMapEntryFilter(SVNStatusType.STATUS_MODIFIED,
              SVNStatusType.STATUS_ADDED, SVNStatusType.STATUS_UNVERSIONED))
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
  }

  @Override
  public void update() throws ScmException {
    // TODO Auto-generated method stub

  }

  @Override
  public void tag(TagRequest request) throws ScmException {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean hasTag(String tagName) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void deleteTag(String tagName) throws ScmException {
    // TODO Auto-generated method stub

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
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String calculateBranchConnectionString(String currentConnectionString, String branchName) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isTagInfoIncludedInConnection() {
    // TODO Auto-generated method stub
    return false;
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
}

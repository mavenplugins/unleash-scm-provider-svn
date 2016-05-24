package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;
import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.annotations.ScmProviderType;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNDirEntryNameChecker;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNUrlUtils;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNUtil;
import com.itemis.maven.plugins.unleash.scm.providers.util.collection.SVNStatusToFile;
import com.itemis.maven.plugins.unleash.scm.providers.util.collection.WCRelativePathToFile;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteTagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.TagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.UpdateRequest;

@ScmProviderType("svn")
public class ScmProviderSVN implements ScmProvider {
  private SVNClientManager clientManager;
  private File workingDir;
  private SVNUtil util;

  @Override
  public void initialize(File workingDirectory, Optional<String> username, Optional<String> password) {
    this.workingDir = workingDirectory;
    if (username.isPresent()) {
      this.clientManager = SVNClientManager.newInstance(null, username.get(), password.get());
    } else {
      this.clientManager = SVNClientManager.newInstance();
    }
    this.util = new SVNUtil(this.clientManager, this.workingDir);
  }

  @Override
  public void close() {
    this.util = null;
    this.clientManager.dispose();
  }

  @Override
  public String commit(CommitRequest request) throws ScmException {
    try {
      Collection<File> filesToCommit = this.util.getFilesToCommit(true, request.getPathsToCommit());
      if (!filesToCommit.isEmpty()) {
        // merge the outdated files (if collection is empty nothing happens)
        Collection<SVNStatus> outdatedFilesStati = this.util.getOutdatedFiles(filesToCommit);

        Optional<UpdateRequest> updateRequest = this.util.createUpdateRequestForFiles(
            Collections2.transform(outdatedFilesStati, SVNStatusToFile.INSTANCE), SVNRevision.HEAD,
            request.getMergeStrategy(), request.getMergeClient());
        if (updateRequest.isPresent()) {
          update(updateRequest.get());
        }

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
  public String update(UpdateRequest request) throws ScmException {
    try {
      SVNUpdateClient updateClient = this.clientManager.getUpdateClient();
      DefaultSVNOptions options = (DefaultSVNOptions) updateClient.getOptions();
      options.setConflictHandler(new SVNMergeConflictResolver(request.getMergeStrategy(), request.getMergeClient()));
      updateClient.setUpdateLocksOnDemand(true);

      SVNRevision revision = SVNRevision.HEAD;
      if (request.getTargetRevision().isPresent()) {
        revision = SVNRevision.parse(request.getTargetRevision().get());
      }

      Collection<File> filesToUpdate = Collections2.transform(request.getPathsToUpdate(),
          new WCRelativePathToFile(this.workingDir));
      long newRevision = -1;
      if (filesToUpdate.isEmpty()) {
        newRevision = updateClient.doUpdate(this.workingDir, revision, SVNDepth.INFINITY, true, false);
      } else {
        long[] newRevisions = updateClient.doUpdate(filesToUpdate.toArray(new File[filesToUpdate.size()]), revision,
            SVNDepth.INFINITY, true, false, true);
        for (long rev : newRevisions) {
          newRevision = Math.max(newRevision, rev);
        }
      }

      return String.valueOf(newRevision);
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.UPDATE,
          "Could not update local working copy with changes from remote SVN repository.", e);
    }
  }

  @Override
  public String tag(TagRequest request) throws ScmException {
    String currentUrl = this.util.getCurrentConnectionUrl();
    SVNCopySource source;
    if (request.commitBeforeTagging()) {
      // 1. commit the changes (no merging!
      CommitRequest cr = CommitRequest.builder().message(request.getPreTagCommitMessage()).noMerge().build();
      String newRevision = commit(cr);

      // 2. set the source to the remote url with the new revision from the commit
      SVNRevision tagRevision = SVNRevision.parse(newRevision);
      source = new SVNCopySource(tagRevision, tagRevision, SVNUrlUtils.toSVNURL(currentUrl));
    } else {
      // 1. add all unversioned files (utility method is able to do that implicitly)
      this.util.getFilesToCommit(true, Collections.<String> emptySet());

      // 2. set the source to the local working copy with local revisions
      source = new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, this.workingDir);
    }

    try {
      String tagUrl = calculateTagConnectionString(currentUrl, request.getTagName());
      SVNCopyClient copyClient = this.clientManager.getCopyClient();
      SVNCommitInfo info = copyClient.doCopy(new SVNCopySource[] { source }, SVNUrlUtils.toSVNURL(tagUrl), false, true,
          true, request.getMessage(), null);
      return String.valueOf(info.getNewRevision());
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.TAG, "An error occurred during SVN tag creation.", e);
    }
  }

  @Override
  public boolean hasTag(final String tagName) {
    SVNLogClient logClient = this.clientManager.getLogClient();
    String tagsUrl = SVNUrlUtils.getBaseTagsUrl(this.util.getCurrentConnectionUrl());
    try {
      SVNDirEntryNameChecker nameChecker = new SVNDirEntryNameChecker(tagName);
      logClient.doList(SVNUrlUtils.toSVNURL(tagsUrl), SVNRevision.HEAD, SVNRevision.HEAD, false, false, nameChecker);
      return nameChecker.isPresent();
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.TAG,
          "An error occurred while querying the remote SVN repository for tag '" + tagName + "'.", e);
    }
  }

  @Override
  public String deleteTag(DeleteTagRequest request) throws ScmException {
    SVNCommitClient commitClient = this.clientManager.getCommitClient();
    String tagUrl = calculateTagConnectionString(this.util.getCurrentConnectionUrl(), request.getTagName());
    try {
      SVNCommitInfo info = commitClient.doDelete(new SVNURL[] { SVNUrlUtils.toSVNURL(tagUrl) }, request.getMessage());
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
    return SVNUrlUtils.getBaseTagsUrl(currentConnectionString) + "/" + tagName;
  }

  @Override
  public String calculateBranchConnectionString(String currentConnectionString, String branchName) {
    return SVNUrlUtils.getBaseBranchesUrl(currentConnectionString) + "/" + branchName;
  }

  @Override
  public boolean isTagInfoIncludedInConnection() {
    return true;
  }

}

package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;
import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.ScmProviderInitialization;
import com.itemis.maven.plugins.unleash.scm.annotations.ScmProviderType;
import com.itemis.maven.plugins.unleash.scm.providers.util.NullOutputStream;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNDirEntryNameChecker;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNHistoryLogEntryHandler;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNUrlUtils;
import com.itemis.maven.plugins.unleash.scm.providers.util.SVNUtil;
import com.itemis.maven.plugins.unleash.scm.providers.util.collection.FileToWCRelativePath;
import com.itemis.maven.plugins.unleash.scm.providers.util.collection.SVNDiffGenerator;
import com.itemis.maven.plugins.unleash.scm.providers.util.collection.SVNStatusToFile;
import com.itemis.maven.plugins.unleash.scm.providers.util.collection.WCRelativePathToFile;
import com.itemis.maven.plugins.unleash.scm.requests.BranchRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CheckoutRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest.Builder;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteBranchRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteTagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DiffRequest;
import com.itemis.maven.plugins.unleash.scm.requests.HistoryRequest;
import com.itemis.maven.plugins.unleash.scm.requests.PushRequest;
import com.itemis.maven.plugins.unleash.scm.requests.RevertCommitsRequest;
import com.itemis.maven.plugins.unleash.scm.requests.TagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.UpdateRequest;
import com.itemis.maven.plugins.unleash.scm.results.DiffObject;
import com.itemis.maven.plugins.unleash.scm.results.DiffResult;
import com.itemis.maven.plugins.unleash.scm.results.HistoryResult;

@ScmProviderType("svn")
public class ScmProviderSVN implements ScmProvider {
  private static final String LOG_PREFIX = "SVN - ";
  private SVNClientManager clientManager;
  private File workingDir;
  private SVNUtil util;
  private Logger log;

  @Override
  public void initialize(ScmProviderInitialization initialization) {
    disableKeyring();

    this.workingDir = initialization.getWorkingDirectory();
    if (this.workingDir.exists()) {
      Preconditions.checkArgument(this.workingDir.isDirectory(),
          "The configured working directory is not a directory!");
    }
    this.log = initialization.getLogger().or(Logger.getLogger(ScmProvider.class.getName()));

    if (initialization.getUsername().isPresent()) {
      this.clientManager = SVNClientManager.newInstance(null, initialization.getUsername().get(),
          initialization.getPassword().or(StringUtils.EMPTY));
    } else {
      this.clientManager = SVNClientManager.newInstance();
    }
    this.util = new SVNUtil(this.clientManager, this.workingDir);
  }

  private void disableKeyring() {
    System.setProperty("svnkit.library.gnome-keyring.enabled", "false");
  }

  @Override
  public void close() {
    this.util = null;
    this.clientManager.dispose();
  }

  @Override
  public void checkout(CheckoutRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Checking out from remote repository.");
    }

    String url = null;
    if (request.checkoutBranch()) {
      url = calculateBranchConnectionString(request.getRemoteRepositoryUrl(), request.getBranch().get());
    } else if (request.checkoutTag()) {
      url = calculateTagConnectionString(request.getRemoteRepositoryUrl(), request.getTag().get());
    } else {
      url = request.getRemoteRepositoryUrl();
    }

    // check if local working dir is empty
    if (this.workingDir.exists() && this.workingDir.list().length > 0) {
      throw new ScmException(ScmOperation.CHECKOUT, "Unable to checkout remote repository '" + url
          + "'. Local working directory '" + this.workingDir.getAbsolutePath() + "' is not empty!");
    }

    SVNRevision revisionToCheckout = SVNUrlUtils.toSVNRevisionOrHEAD(request.getRevision());
    SVNDepth checkoutDepth = request.checkoutWholeRepository() ? SVNDepth.INFINITY : SVNDepth.EMPTY;

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX + "Checkout info:\n");
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- URL: ").append(url).append('\n');
      message.append("\t- REVISION: ").append(revisionToCheckout).append('\n');
      message.append("\t- DEPTH: ").append(checkoutDepth);
      if (!request.checkoutWholeRepository()) {
        message.append("\n\t- FILES: ").append(Joiner.on(',').join(request.getPathsToCheckout()));
      }
      this.log.fine(message.toString());
    }

    try {
      SVNUpdateClient updateClient = this.clientManager.getUpdateClient();
      updateClient.setIgnoreExternals(false);
      updateClient.doCheckout(SVNUrlUtils.toSVNURL(url), this.workingDir, revisionToCheckout, revisionToCheckout,
          checkoutDepth, false);

      if (!request.checkoutWholeRepository()) {
        Collection<File> filesToUpdate = Collections2.transform(request.getPathsToCheckout(),
            new WCRelativePathToFile(this.workingDir));
        updateClient.doUpdate(filesToUpdate.toArray(new File[filesToUpdate.size()]), revisionToCheckout,
            SVNDepth.INFINITY, false, false, true);
      }

      if (this.log.isLoggable(Level.INFO)) {
        this.log.info(LOG_PREFIX + "Checkout finished successfully!");
      }
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.CHECKOUT,
          "An error occurred during the checkout of the remote SVN repository: " + url, e);
    }
  }

  @Override
  public String commit(CommitRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Committing to remote repository.");
    }

    try {
      Collection<File> filesToCommit = this.util.getFilesToCommit(request.includeUntrackedFiles(),
          request.getPathsToCommit());
      if (!filesToCommit.isEmpty()) {
        if (this.log.isLoggable(Level.FINE)) {
          StringBuilder message = new StringBuilder(LOG_PREFIX + "Commit info:\n");
          message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
          message.append("\t- URL: ").append(this.util.getCurrentConnectionUrl()).append('\n');
          message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy()).append('\n');
          message.append("\t- FILES: ").append(
              Joiner.on(',').join(Collections2.transform(filesToCommit, new FileToWCRelativePath(this.workingDir))));
          this.log.fine(message.toString());
        }

        // merge the outdated files (if collection is empty nothing happens)
        Collection<SVNStatus> outdatedFilesStati = this.util.getOutdatedFiles(filesToCommit);
        Optional<UpdateRequest> updateRequest = this.util.createUpdateRequestForFiles(
            Collections2.transform(outdatedFilesStati, SVNStatusToFile.INSTANCE), SVNRevision.HEAD,
            request.getMergeStrategy(), request.getMergeClient());
        if (updateRequest.isPresent()) {
          update(updateRequest.get());
        }

        SVNWCClient workingCopyClient = this.clientManager.getWCClient();
        for (File file : filesToCommit) {
          if (!file.exists()) {
            workingCopyClient.doDelete(file, true, false);
          }
        }

        SVNCommitClient commitClient = this.clientManager.getCommitClient();
        SVNCommitInfo info = commitClient.doCommit(filesToCommit.toArray(new File[filesToCommit.size()]), true,
            request.getMessage(), null, null, true, true, SVNDepth.INFINITY);

        long newRevision = info.getNewRevision();
        if (this.log.isLoggable(Level.INFO)) {
          this.log.info(LOG_PREFIX + "Commit finished successfully. New remote revision is: " + newRevision);
        }
        return String.valueOf(newRevision);
      }

      String localRevision = getLocalRevision();
      if (this.log.isLoggable(Level.INFO)) {
        this.log.info(LOG_PREFIX + "There was nothing to commit. Current revision is: " + localRevision);
      }
      return localRevision;
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.COMMIT, "Could not commit changes to the remote SVN repository.", e);
    }
  }

  @Override
  public String push(PushRequest request) throws ScmException {
    // Nothing to do here since all modifying svn operations are remote by nature!
    return getLatestRemoteRevision();
  }

  @Override
  public String update(UpdateRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Updating local working copy.");
    }

    try {
      SVNUpdateClient updateClient = this.clientManager.getUpdateClient();
      DefaultSVNOptions options = (DefaultSVNOptions) updateClient.getOptions();
      options.setConflictHandler(new SVNMergeConflictResolver(request.getMergeStrategy(), request.getMergeClient()));
      updateClient.setUpdateLocksOnDemand(true);

      SVNRevision revision = SVNUrlUtils.toSVNRevisionOrHEAD(request.getTargetRevision());
      Collection<File> filesToUpdate = Collections2.transform(request.getPathsToUpdate(),
          new WCRelativePathToFile(this.workingDir));

      if (this.log.isLoggable(Level.FINE)) {
        StringBuilder message = new StringBuilder(LOG_PREFIX + "Update info:\n");
        message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
        message.append("\t- URL: ").append(this.util.getCurrentConnectionUrl()).append('\n');
        message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy());
        if (!filesToUpdate.isEmpty()) {
          message.append("\n\t- FILES: ").append(Joiner.on(',').join(request.getPathsToUpdate()));
        }
        this.log.fine(message.toString());
      }

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

      if (this.log.isLoggable(Level.INFO)) {
        this.log
            .info(LOG_PREFIX + "Update of local working copy finished successfully. New revision is: " + newRevision);
      }
      return String.valueOf(newRevision);
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.UPDATE,
          "Could not update local working copy with changes from remote SVN repository.", e);
    }
  }

  @Override
  public String tag(TagRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Creating SVN tag '" + request.getTagName() + "'");
    }

    // take the requested URL and the URL of the working dir as a fallback
    String url = request.tagFromWorkingCopy() ? this.util.getCurrentConnectionUrl()
        : request.getRemoteRepositoryUrl().get();

    SVNCopySource source;
    if (request.tagFromWorkingCopy()) {
      if (request.commitBeforeTagging()) {
        // 1. commit the changes (no merging!)
        Builder builder = CommitRequest.builder().message(
            request.getPreTagCommitMessage().or("Preparation for tag creation (Name: '" + request.getTagName() + "')."))
            .noMerge();
        if (request.includeUntrackedFiles()) {
          builder.includeUntrackedFiles();
        }
        String newRevision = commit(builder.build());

        // 2. set the source to the remote url with the new revision from the commit
        SVNRevision tagRevision = SVNUrlUtils.toSVNRevisionOrHEAD(Optional.of(newRevision));
        source = new SVNCopySource(tagRevision, tagRevision, SVNUrlUtils.toSVNURL(url));
      } else {
        // 1. add all unversioned files (utility method is able to do that implicitly)
        this.util.getFilesToCommit(request.includeUntrackedFiles(), Collections.<String> emptySet());

        // 2. set the source to the local working copy with local revisions
        source = new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, this.workingDir);
      }
    } else {
      SVNRevision revision = SVNUrlUtils.toSVNRevisionOrHEAD(request.getRevision());
      source = new SVNCopySource(revision, revision, SVNUrlUtils.toSVNURL(url));
    }

    try {
      String tagUrl = calculateTagConnectionString(url, request.getTagName());

      if (this.log.isLoggable(Level.FINE)) {
        StringBuilder message = new StringBuilder(LOG_PREFIX + "Tag info:\n");
        message.append("\t- TAG_NAME: ").append(request.getTagName()).append('\n');
        message.append("\t- TAG_URL: ").append(tagUrl).append('\n');
        if (request.tagFromWorkingCopy()) {
          message.append("\t- TAG_SOURCE: WORKING_COPY (").append(url).append(")\n");
          message.append("\t- COMMIT_BEFORE: ").append(request.commitBeforeTagging());
        } else {
          message.append("\t- TAG_SOURCE: ").append(url).append('\n');
          message.append("\t- REVISION: ").append(request.getRevision());
        }
        this.log.fine(message.toString());
      }

      SVNCopyClient copyClient = this.clientManager.getCopyClient();
      SVNCommitInfo info = copyClient.doCopy(new SVNCopySource[] { source }, SVNUrlUtils.toSVNURL(tagUrl), false, true,
          true, request.getMessage(), null);

      long newRevision = info.getNewRevision();
      if (this.log.isLoggable(Level.INFO)) {
        this.log
            .info(LOG_PREFIX + "Tagging of remote repository finished successfully. New revision is: " + newRevision);
      }
      return String.valueOf(newRevision);
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.TAG, "An error occurred during SVN tag creation.", e);
    }
  }

  @Override
  public boolean hasTag(final String tagName) {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Searching SVN tag '" + tagName + "'");
    }

    SVNLogClient logClient = this.clientManager.getLogClient();
    String tagsUrl = SVNUrlUtils.getBaseTagsUrl(this.util.getCurrentConnectionUrl());
    try {
      if (this.log.isLoggable(Level.FINE)) {
        StringBuilder message = new StringBuilder(LOG_PREFIX + "Query info:\n");
        message.append("\t- TAGS_FOLDER_URL: ").append(tagsUrl).append('\n');
        message.append("\t- TAG_NAME: ").append(tagName);
        this.log.fine(message.toString());
      }
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
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Deleting SVN tag");
    }

    SVNCommitClient commitClient = this.clientManager.getCommitClient();
    String tagUrl = calculateTagConnectionString(this.util.getCurrentConnectionUrl(), request.getTagName());
    try {
      if (this.log.isLoggable(Level.FINE)) {
        StringBuilder message = new StringBuilder(LOG_PREFIX + "Tag info:\n");
        message.append("\t- TAG_NAME: ").append(request.getTagName()).append('\n');
        message.append("\t- TAG_URL: ").append(tagUrl);
        this.log.fine(message.toString());
      }
      SVNCommitInfo info = commitClient.doDelete(new SVNURL[] { SVNUrlUtils.toSVNURL(tagUrl) }, request.getMessage());
      return String.valueOf(info.getNewRevision());
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.DELETE_TAG,
          "An error occurred during the deletion of the SVN tag '" + request.getTagName() + "'. SVN url: " + tagUrl, e);
    }
  }

  @Override
  public String branch(BranchRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Creating SVN branch.");
    }

    // take the requested URL and the URL of the working dir as a fallback
    String url = request.branchFromWorkingCopy() ? this.util.getCurrentConnectionUrl()
        : request.getRemoteRepositoryUrl().get();

    SVNCopySource source;
    if (request.branchFromWorkingCopy()) {
      if (request.commitBeforeBranching()) {
        // 1. commit the changes (no merging!)
        CommitRequest cr = CommitRequest.builder().message(request.getPreBranchCommitMessage()).noMerge().build();
        String newRevision = commit(cr);

        // 2. set the source to the remote url with the new revision from the commit
        SVNRevision branchRevision = SVNUrlUtils.toSVNRevisionOrHEAD(Optional.of(newRevision));
        source = new SVNCopySource(branchRevision, branchRevision, SVNUrlUtils.toSVNURL(url));
      } else {
        // 1. add all unversioned files (utility method is able to do that implicitly)
        this.util.getFilesToCommit(true, Collections.<String> emptySet());

        // 2. set the source to the local working copy with local revisions
        source = new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, this.workingDir);
      }
    } else {
      SVNRevision revision = SVNUrlUtils.toSVNRevisionOrHEAD(request.getRevision());
      source = new SVNCopySource(revision, revision, SVNUrlUtils.toSVNURL(url));
    }

    try {
      String branchUrl = calculateBranchConnectionString(url, request.getBranchName());

      if (this.log.isLoggable(Level.FINE)) {
        StringBuilder message = new StringBuilder(LOG_PREFIX + "Branch info:\n");
        message.append("\t- BRANCH_NAME: ").append(request.getBranchName()).append('\n');
        message.append("\t- BRANCH_URL: ").append(branchUrl).append('\n');
        if (request.branchFromWorkingCopy()) {
          message.append("\t- BRANCH_SOURCE: WORKING_COPY (").append(url).append(")\n");
          message.append("\t- COMMIT_BEFORE: ").append(request.commitBeforeBranching());
        } else {
          message.append("\t- BRANCH_SOURCE: ").append(url).append('\n');
          message.append("\t- REVISION: ").append(request.getRevision());
        }
        this.log.fine(message.toString());
      }

      SVNCopyClient copyClient = this.clientManager.getCopyClient();
      SVNCommitInfo info = copyClient.doCopy(new SVNCopySource[] { source }, SVNUrlUtils.toSVNURL(branchUrl), false,
          true, true, request.getMessage(), null);

      long newRevision = info.getNewRevision();
      if (this.log.isLoggable(Level.INFO)) {
        this.log
            .info(LOG_PREFIX + "Branching of remote repository finished successfully. New revision is: " + newRevision);
      }
      return String.valueOf(newRevision);
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.BRANCH, "An error occurred during SVN branch creation.", e);
    }
  }

  @Override
  public boolean hasBranch(String branchName) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Searching SVN branch");
    }

    SVNLogClient logClient = this.clientManager.getLogClient();
    String branchesUrl = SVNUrlUtils.getBaseBranchesUrl(this.util.getCurrentConnectionUrl());
    try {
      if (this.log.isLoggable(Level.FINE)) {
        StringBuilder message = new StringBuilder(LOG_PREFIX + "Query info:\n");
        message.append("\t- BRANCHES_FOLDER_URL: ").append(branchesUrl).append('\n');
        message.append("\t- BRANCH_NAME: ").append(branchName);
        this.log.fine(message.toString());
      }
      SVNDirEntryNameChecker nameChecker = new SVNDirEntryNameChecker(branchName);
      logClient.doList(SVNUrlUtils.toSVNURL(branchesUrl), SVNRevision.HEAD, SVNRevision.HEAD, false, false,
          nameChecker);
      return nameChecker.isPresent();
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.BRANCH,
          "An error occurred while querying the remote SVN repository for branch '" + branchName + "'.", e);
    }
  }

  @Override
  public String deleteBranch(DeleteBranchRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Deleting SVN branch");
    }

    SVNCommitClient commitClient = this.clientManager.getCommitClient();
    String branchUrl = calculateBranchConnectionString(this.util.getCurrentConnectionUrl(), request.getBranchName());
    try {
      if (this.log.isLoggable(Level.FINE)) {
        StringBuilder message = new StringBuilder(LOG_PREFIX + "Branch info:\n");
        message.append("\t- BRANCH_NAME: ").append(request.getBranchName()).append('\n');
        message.append("\t- BRANCH_URL: ").append(branchUrl);
        this.log.fine(message.toString());
      }
      SVNCommitInfo info = commitClient.doDelete(new SVNURL[] { SVNUrlUtils.toSVNURL(branchUrl) },
          request.getMessage());
      return String.valueOf(info.getNewRevision());
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.DELETE_BRANCH, "An error occurred during the deletion of the SVN branch '"
          + request.getBranchName() + "'. SVN url: " + branchUrl, e);
    }
  }

  @Override
  public String revertCommits(RevertCommitsRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Reverting SVN commits");
    }

    String latestRemoteRevision = getLatestRemoteRevision();
    String from = request.getFromRevision();
    String to = request.getToRevision();
    int revisionDiff = from.compareTo(to);
    if (revisionDiff == 0) {
      // nothing to revert! return the latest remote version
      return latestRemoteRevision;
    } else if (revisionDiff < 0) {
      // older from version (wrong direction!
      throw new ScmException(ScmOperation.REVERT_COMMITS,
          "Error reverting commits in remote repository. \"FROM\" revision (" + from
              + ") is older than \"TO\" revision (" + to + ")");
    } else if (latestRemoteRevision.compareTo(from) < 0 || latestRemoteRevision.compareTo(to) < 0) {
      // older from version (wrong direction!
      throw new ScmException(ScmOperation.REVERT_COMMITS,
          "Error reverting commits in remote repository. \"FROM\" revision (" + from + ") or \"TO\" revision (" + to
              + ") is newer than the head revision (" + latestRemoteRevision + ")");
    }

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Commit info:\n");
      message.append("\t- FROM: ").append(request.getFromRevision()).append('\n');
      message.append("\t- TO: ").append(request.getToRevision()).append('\n');
      message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy()).append('\n');
      this.log.fine(message.toString());
    }

    // update to HEAD revision first then revert commits!
    UpdateRequest updateRequest = UpdateRequest.builder().mergeStrategy(request.getMergeStrategy())
        .mergeClient(request.getMergeClient().orNull()).build();
    update(updateRequest);

    SVNURL url = SVNUrlUtils.toSVNURL(this.util.getCurrentConnectionUrl());
    SVNRevision toRevision = SVNUrlUtils.toSVNRevisionOrHEAD(Optional.of(to));
    SVNRevision fromRevision = SVNUrlUtils.toSVNRevisionOrHEAD(Optional.of(from));

    try {
      SVNDiffClient diffClient = this.clientManager.getDiffClient();
      DefaultSVNOptions options = (DefaultSVNOptions) diffClient.getOptions();
      options.setConflictHandler(new SVNMergeConflictResolver(request.getMergeStrategy(), request.getMergeClient()));
      diffClient.doMerge(url, fromRevision, url, toRevision, this.workingDir, SVNDepth.INFINITY, false, true, false,
          false);
    } catch (Exception e) {
      throw new ScmException(ScmOperation.REVERT_COMMITS, "An error occurred during the reversion of some SVN commits.",
          e);
    }

    CommitRequest commitRequest = CommitRequest.builder().merge().mergeClient(request.getMergeClient().orNull())
        .message(request.getMessage()).build();
    String newRevision = commit(commitRequest);

    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Revert finished successfully. New revision is: " + newRevision);
    }

    return newRevision;
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
      return String.valueOf(
          status != null ? Math.max(status.getRemoteRevision().getNumber(), status.getRevision().getNumber()) : -1);
    } catch (SVNException e) {
      throw new IllegalStateException("Could not retrieve remote SVN revision", e);
    }
  }

  @Override
  public String calculateTagConnectionString(String currentConnectionString, String tagName) {
    return SVNUrlUtils.getBaseTagsUrl(currentConnectionString) + "/" + tagName
        + SVNUrlUtils.getUrlSubPath(currentConnectionString);
  }

  @Override
  public String calculateBranchConnectionString(String currentConnectionString, String branchName) {
    String baseUrl;
    if (Objects.equal("trunk", branchName)) {
      baseUrl = SVNUrlUtils.getBaseUrl(currentConnectionString);
    } else {
      baseUrl = SVNUrlUtils.getBaseBranchesUrl(currentConnectionString);
    }
    return baseUrl + "/" + branchName + SVNUrlUtils.getUrlSubPath(currentConnectionString);
  }

  @Override
  public boolean isTagInfoIncludedInConnection() {
    // because tag urls are ${REPO_BASE_URL}/tags/${TAG_NAME}
    return true;
  }

  @Override
  // TODO logging!
  public HistoryResult getHistory(final HistoryRequest request) throws ScmException {
    String connectionUrl = null;
    if (request.getRemoteRepositoryUrl().isPresent()) {
      connectionUrl = request.getRemoteRepositoryUrl().get();
    } else {
      connectionUrl = this.util.getCurrentConnectionUrl();
    }

    SVNURL url = SVNUrlUtils.toSVNURL(connectionUrl);
    SVNRevision startRevision = getTagRevisionOrDefault(connectionUrl, request.getStartTag(),
        request.getStartRevision().or("0"));
    SVNRevision endRevision = getTagRevisionOrDefault(connectionUrl, request.getEndTag(),
        request.getEndRevision().or(SVNRevision.HEAD.getName()));

    try {
      SVNHistoryLogEntryHandler handler = new SVNHistoryLogEntryHandler(request.getMessageFilters(),
          request.getMaxResults());

      if (request.getMessageFilters().isEmpty()) {
        // if no filters are applied we can optimize the query and request only the limited number of entries
        this.clientManager.getLogClient().doLog(url, null, null, startRevision, endRevision, false, false,
            request.getMaxResults(), handler);
      } else {
        // if filters are applied we need to request the whole log and filter afterwards
        this.clientManager.getLogClient().doLog(url, null, null, startRevision, endRevision, false, false, -1, handler);
      }

      return handler.getHistory();
    } catch (SVNException e) {
      throw new ScmException(ScmOperation.INFO, "Unable to retrieve the SVN log history.", e);
    }
  }

  private SVNRevision getTagRevisionOrDefault(String connectionUrl, Optional<String> tag, String defaultRevision) {
    String revision = defaultRevision;
    if (tag.isPresent()) {
      String tagConnectionString = calculateTagConnectionString(connectionUrl, tag.get());
      try {
        SVNInfo info = this.clientManager.getWCClient().doInfo(SVNUrlUtils.toSVNURL(tagConnectionString), null,
            SVNRevision.HEAD);
        revision = Long.toString(info.getCommittedRevision().getNumber());
      } catch (Exception e) {
        throw new ScmException(ScmOperation.INFO, "Unable to get revision of the following URL: " + tagConnectionString,
            e);
      }
    }
    SVNRevision startRevision = SVNRevision.parse(revision);
    return startRevision;
  }

  @Override
  public DiffResult getDiff(final DiffRequest request) throws ScmException {
    SVNURL sourceUrl = SVNUrlUtils
        .toSVNURL(request.getSourceRemoteRepositoryUrl().or(this.util.getCurrentConnectionUrl()));
    SVNURL targetUrl = SVNUrlUtils
        .toSVNURL(request.getTargetRemoteRepositoryUrl().or(this.util.getCurrentConnectionUrl()));

    SVNRevision sourceRevision = SVNUrlUtils.toSVNRevisionOrHEAD(request.getSourceRevision());
    if (Objects.equal(SVNRevision.HEAD, sourceRevision)) {
      try {
        long rev = this.util.getRemoteRevision(sourceUrl);
        sourceRevision = SVNRevision.parse(String.valueOf(rev));
      } catch (SVNException e) {
        this.log.fine("Could not determine remote revision of SVN URL '" + sourceUrl.toDecodedString()
            + "'. Using 'HEAD' instead to calculate the diff.");
      }
    }

    SVNRevision targetRevision = SVNUrlUtils.toSVNRevisionOrHEAD(request.getTargetRevision());
    if (Objects.equal(SVNRevision.HEAD, targetRevision)) {
      try {
        long rev = this.util.getRemoteRevision(targetUrl);
        targetRevision = SVNRevision.parse(String.valueOf(rev));
      } catch (SVNException e) {
        this.log.fine("Could not determine remote revision of SVN URL '" + targetUrl.toDecodedString()
            + "'. Using 'HEAD' instead to calculate the diff.");
      }
    }

    DiffResult.Builder resultBuilder = DiffResult.builder();

    try {
      SVNDiffClient diffClient = this.clientManager.getDiffClient();
      SVNDiffGenerator diffGenerator = new SVNDiffGenerator(request.getType());
      diffClient.setDiffGenerator(diffGenerator);
      diffClient.doDiff(sourceUrl, sourceRevision, targetUrl, targetRevision, SVNDepth.INFINITY, true,
          new NullOutputStream());

      for (DiffObject diff : diffGenerator.getDiffs()) {
        resultBuilder.addDiff(diff);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return resultBuilder.build();
  }

}

package com.itemis.maven.plugins.unleash.scm.providers.util;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.itemis.maven.plugins.unleash.scm.ScmException;

public class SVNUrlUtils {

  public static String getBaseTagsUrl(String currentUrl) throws ScmException {
    Preconditions.checkArgument(currentUrl != null);
    Preconditions.checkArgument(!currentUrl.isEmpty());
    return getBaseUrl(currentUrl) + "/tags";
  }

  public static String getBaseBranchesUrl(String currentUrl) throws ScmException {
    Preconditions.checkArgument(currentUrl != null);
    Preconditions.checkArgument(!currentUrl.isEmpty());
    return getBaseUrl(currentUrl) + "/branches";
  }

  public static String getBaseUrl(String currentUrl) throws ScmException {
    Preconditions.checkArgument(currentUrl != null);
    Preconditions.checkArgument(!currentUrl.isEmpty());

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

  public static SVNURL toSVNURL(String urlString) throws ScmException, IllegalArgumentException {
    Preconditions.checkArgument(urlString != null);
    Preconditions.checkArgument(!urlString.isEmpty());

    try {
      return SVNURL.parseURIEncoded(urlString);
    } catch (SVNException e) {
      throw new ScmException("Unable to parse the following SVN conncetion URL: " + urlString);
    }
  }

  public static SVNRevision toSVNRevisionOrHEAD(Optional<String> revision) {
    return SVNRevision.parse(revision.or(SVNRevision.HEAD.getName()));
  }
}

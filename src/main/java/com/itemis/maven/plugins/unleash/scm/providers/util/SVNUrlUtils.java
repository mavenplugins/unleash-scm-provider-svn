package com.itemis.maven.plugins.unleash.scm.providers.util;

import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.itemis.maven.plugins.unleash.scm.ScmException;

public class SVNUrlUtils {
  private static final String PATH_SEGMENT_BRANCHES = "branches";
  private static final String PATH_SEGMENT_TAGS = "tags";
  private static final String PATH_SEGMENT_TRUNK = "trunk";
  private static final String PATH_SEPARATOR = "/";
  private static final String SUBPATH_BRANCHES = PATH_SEPARATOR + PATH_SEGMENT_BRANCHES;
  private static final String SUBPATH_TAGS = PATH_SEPARATOR + PATH_SEGMENT_TAGS;
  private static final String SUBPATH_TRUNK = PATH_SEPARATOR + PATH_SEGMENT_TRUNK;

  public static String getBaseTagsUrl(String currentUrl) throws ScmException {
    Preconditions.checkArgument(currentUrl != null);
    Preconditions.checkArgument(!currentUrl.isEmpty());
    return getBaseUrl(currentUrl) + SUBPATH_TAGS;
  }

  public static String getBaseBranchesUrl(String currentUrl) throws ScmException {
    Preconditions.checkArgument(currentUrl != null);
    Preconditions.checkArgument(!currentUrl.isEmpty());
    return getBaseUrl(currentUrl) + SUBPATH_BRANCHES;
  }

  public static String getBaseUrl(String currentUrl) throws ScmException {
    Preconditions.checkArgument(currentUrl != null);
    Preconditions.checkArgument(!currentUrl.isEmpty());

    int trunkPos = currentUrl.indexOf(SUBPATH_TRUNK);
    int branchesPos = currentUrl.indexOf(SUBPATH_BRANCHES);
    int tagsPos = currentUrl.indexOf(SUBPATH_TAGS);
    if (trunkPos > -1) {
      return currentUrl.substring(0, trunkPos);
    } else if (branchesPos > -1) {
      return currentUrl.substring(0, branchesPos);
    } else if (tagsPos > -1) {
      return currentUrl.substring(0, tagsPos);
    }
    return currentUrl;
  }

  public static String getUrlSubPath(String currentUrl) {
    if (!currentUrl.contains(SUBPATH_BRANCHES) && !currentUrl.contains(SUBPATH_TAGS)
        && !currentUrl.contains(SUBPATH_TRUNK)) {
      return "";
    }

    List<String> split = Splitter.on(PATH_SEPARATOR).splitToList(currentUrl);
    int startIndex = -1;
    for (int i = split.size() - 1; i >= 0; i--) {
      String segment = split.get(i);
      if (PATH_SEGMENT_TRUNK.equals(segment)) {
        startIndex = i + 1;
        break;
      } else if (PATH_SEGMENT_BRANCHES.equals(segment) || PATH_SEGMENT_TAGS.equals(segment)) {
        startIndex = i + 2;
        break;
      }
    }

    if (startIndex == -1 || startIndex >= split.size()) {
      return "";
    }
    return "/" + Joiner.on('/').join(split.subList(startIndex, split.size()));
  }

  public static SVNURL toSVNURL(String urlString) throws ScmException, IllegalArgumentException {
    Preconditions.checkArgument(urlString != null);
    Preconditions.checkArgument(!urlString.isEmpty());

    try {
      return SVNURL.parseURIEncoded(urlString);
    } catch (SVNException e) {
      throw new ScmException("Unable to parse the following SVN connection URL: " + urlString);
    }
  }

  public static SVNRevision toSVNRevisionOrHEAD(Optional<String> revision) {
    return SVNRevision.parse(revision.or(SVNRevision.HEAD.getName()));
  }
}

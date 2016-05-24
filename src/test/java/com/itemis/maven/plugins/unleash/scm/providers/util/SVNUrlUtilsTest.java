package com.itemis.maven.plugins.unleash.scm.providers.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

@RunWith(DataProviderRunner.class)
public class SVNUrlUtilsTest {

  @DataProvider
  public static Object[][] toSVNURL() throws SVNException {
    return new Object[][] { { "http://localhost:3343/test", SVNURL.parseURIEncoded("http://localhost:3343/test") },
        { "svn://localhost:3343/test", SVNURL.parseURIEncoded("svn://localhost:3343/test") } };
  }

  @DataProvider
  public static Object[][] toSVNURL_IAE() {
    return new Object[][] { { null }, { "" } };
  }

  @DataProvider
  public static Object[][] toSVNURL_ScmE() {
    return new Object[][] { { "svn:localhost/test" } };
  }

  @DataProvider
  public static Object[][] getBaseUrl_IAE() {
    return new Object[][] { { null }, { "" } };
  }

  @DataProvider
  public static Object[][] getBaseUrl() {
    return new Object[][] { { "svn://localhost/test", "svn://localhost/test" },
        { "svn://localhost/test/trunk", "svn://localhost/test" },
        { "svn://localhost/test/branches", "svn://localhost/test" },
        { "https://localhost/test/branches/x", "https://localhost/test" },
        { "svn+ssh://localhost/test/tags", "svn+ssh://localhost/test" },
        { "svn://localhost/test/tags/xyz", "svn://localhost/test" },
        { "svn://localhost/test/trunk/f1/x", "svn://localhost/test" } };
  }

  @DataProvider
  public static Object[][] getBaseTagsUrl() {
    return new Object[][] { { "svn://localhost/test", "svn://localhost/test/tags" },
        { "svn://localhost/test/trunk", "svn://localhost/test/tags" },
        { "svn://localhost/test/branches", "svn://localhost/test/tags" },
        { "https://localhost/test/branches/x", "https://localhost/test/tags" },
        { "svn+ssh://localhost/test/tags", "svn+ssh://localhost/test/tags" },
        { "svn://localhost/test/tags/xyz", "svn://localhost/test/tags" },
        { "svn://localhost/test/trunk/f1/x", "svn://localhost/test/tags" } };
  }

  @DataProvider
  public static Object[][] getBaseBranchesUrl() {
    return new Object[][] { { "svn://localhost/test", "svn://localhost/test/branches" },
        { "svn://localhost/test/trunk", "svn://localhost/test/branches" },
        { "svn://localhost/test/branches", "svn://localhost/test/branches" },
        { "https://localhost/test/branches/x", "https://localhost/test/branches" },
        { "svn+ssh://localhost/test/tags", "svn+ssh://localhost/test/branches" },
        { "svn://localhost/test/tags/xyz", "svn://localhost/test/branches" },
        { "svn://localhost/test/trunk/f1/x", "svn://localhost/test/branches" } };
  }

  @Test
  @UseDataProvider("toSVNURL")
  public void testToSVNURL(String url, SVNURL expected) throws SVNException {
    Assert.assertEquals(expected, SVNUrlUtils.toSVNURL(url));
  }

  @Test(expected = IllegalArgumentException.class)
  @UseDataProvider("toSVNURL_IAE")
  public void testToSVNURL_IAE(String url) throws SVNException {
    SVNUrlUtils.toSVNURL(url);
  }

  @Test(expected = ScmException.class)
  @UseDataProvider("toSVNURL_ScmE")
  public void testToSVNURL_SVNE(String url) throws SVNException {
    SVNUrlUtils.toSVNURL(url);
  }

  @Test
  @UseDataProvider("getBaseUrl")
  public void testGetBaseUrl(String url, String expected) {
    Assert.assertEquals(expected, SVNUrlUtils.getBaseUrl(url));
  }

  @Test(expected = IllegalArgumentException.class)
  @UseDataProvider("getBaseUrl_IAE")
  public void testGetBaseUrl_IAE(String url) {
    SVNUrlUtils.getBaseUrl(url);
  }

  @Test
  @UseDataProvider("getBaseTagsUrl")
  public void testGetBaseTagsUrl(String url, String expected) {
    Assert.assertEquals(expected, SVNUrlUtils.getBaseTagsUrl(url));
  }

  @Test(expected = IllegalArgumentException.class)
  @UseDataProvider("getBaseUrl_IAE")
  public void testGetBaseTagsUrl_IAE(String url) {
    SVNUrlUtils.getBaseTagsUrl(url);
  }

  @Test
  @UseDataProvider("getBaseBranchesUrl")
  public void testGetBaseBranchesUrl(String url, String expected) {
    Assert.assertEquals(expected, SVNUrlUtils.getBaseBranchesUrl(url));
  }

  @Test(expected = IllegalArgumentException.class)
  @UseDataProvider("getBaseUrl_IAE")
  public void testGetBaseBranchesUrl_IAE(String url) {
    SVNUrlUtils.getBaseBranchesUrl(url);
  }
}

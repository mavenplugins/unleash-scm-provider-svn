package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;

import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;

public class Main {
  public static void main(String[] args) throws SVNException {
    ScmProvider p = new ScmProviderSVN();
    p.initialize(new File("/home/shillner/work/unleash-maven-plugin/ws/svn-test"));
    try {
      CommitRequest request = CommitRequest.builder().setMessage("aaaaaaaaaaaaaaaaaaaaa").addPaths("xyz/x").push()
          .build();
      String result = p.commit(request);
      System.out.println(result);
    } finally {
      p.close();
    }

    // File workingCopy = new File("/home/shillner/work/unleash-maven-plugin/ws/svn-test");
    //
    // SVNClientManager clientManager = SVNClientManager.newInstance();
    // SVNStatus globalStatus = clientManager.getStatusClient().doStatus(workingCopy, false);
    // clientManager.getStatusClient().doStatus(workingCopy, SVNRevision.WORKING, SVNDepth.INFINITY, false, true, false,
    // false, new ISVNStatusHandler() {
    // @Override
    // public void handleStatus(SVNStatus status) throws SVNException {
    // SVNStatusType statusType = status.getContentsStatus();
    // if (SVNStatusType.STATUS_MODIFIED == statusType) {
    // System.out.println("MODIFIED: " + status.getFile());
    // } else if (SVNStatusType.STATUS_UNVERSIONED == statusType) {
    // System.out.println("NEW: " + status.getFile());
    // }
    // }
    // }, null);
  }
}

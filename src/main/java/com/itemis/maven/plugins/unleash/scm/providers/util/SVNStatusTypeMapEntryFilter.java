package com.itemis.maven.plugins.unleash.scm.providers.util;

import java.util.Map.Entry;
import java.util.Set;

import org.tmatesoft.svn.core.wc.SVNStatusType;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

public class SVNStatusTypeMapEntryFilter implements Predicate<Entry<SVNStatusType, ?>> {
  private Set<SVNStatusType> allowedTypes;

  public SVNStatusTypeMapEntryFilter(SVNStatusType... types) {
    this.allowedTypes = Sets.newHashSet(types);
  }

  @Override
  public boolean apply(Entry<SVNStatusType, ?> input) {
    return this.allowedTypes.contains(input.getKey());
  }
}

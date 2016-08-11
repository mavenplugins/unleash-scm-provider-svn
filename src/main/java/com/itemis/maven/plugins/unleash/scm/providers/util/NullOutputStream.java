package com.itemis.maven.plugins.unleash.scm.providers.util;

import java.io.IOException;
import java.io.OutputStream;

public class NullOutputStream extends OutputStream {
  public NullOutputStream() {
  }

  @Override
  public void write(int b) throws IOException {
    // simply swallows all bytes
  }
}

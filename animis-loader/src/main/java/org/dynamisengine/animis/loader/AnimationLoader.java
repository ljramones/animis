package org.dynamisengine.animis.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface AnimationLoader {
  AnimationLoadResult load(Path path) throws IOException;

  AnimationLoadResult load(InputStream stream, String formatHint) throws IOException;
}

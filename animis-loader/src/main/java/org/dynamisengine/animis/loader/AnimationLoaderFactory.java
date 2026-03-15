package org.dynamisengine.animis.loader;

import java.nio.file.Path;

public final class AnimationLoaderFactory {
  private AnimationLoaderFactory() {}

  public static AnimationLoader forPath(final Path path) {
    if (path == null || path.getFileName() == null) {
      throw new IllegalArgumentException("path must include a file name");
    }
    final String name = path.getFileName().toString().toLowerCase();
    if (name.endsWith(".gltf") || name.endsWith(".glb")) {
      return new GltfAnimationLoader();
    }
    if (name.endsWith(".bvh")) {
      return new BvhAnimationLoader();
    }
    throw new IllegalArgumentException("Unsupported format: " + name);
  }
}

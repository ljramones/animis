module org.dynamisengine.animis.runtime {
  requires org.dynamisengine.animis;
  requires org.dynamisengine.vectrix;
  // MeshForge currently ships as an automatic module named "meshforge".
  // If MeshForge adds module-info.java, update this to its explicit module name.
  requires meshforge;
  requires org.dynamisengine.collision;

  exports org.dynamisengine.animis.runtime.api;
  exports org.dynamisengine.animis.runtime.pose;
  exports org.dynamisengine.animis.runtime.ik;
  exports org.dynamisengine.animis.runtime.physics;
  exports org.dynamisengine.animis.runtime.secondary;
  exports org.dynamisengine.animis.runtime.warp;
  exports org.dynamisengine.animis.runtime.skinning;
  exports org.dynamisengine.animis.runtime.transform;
}

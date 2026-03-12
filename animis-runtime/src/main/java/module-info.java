module org.animis.runtime {
  requires org.animis;
  requires org.dynamisengine.vectrix;
  // MeshForge currently ships as an automatic module named "meshforge".
  // If MeshForge adds module-info.java, update this to its explicit module name.
  requires meshforge;
  requires org.dynamiscollision;

  exports org.animis.runtime.api;
  exports org.animis.runtime.pose;
  exports org.animis.runtime.ik;
  exports org.animis.runtime.physics;
  exports org.animis.runtime.secondary;
  exports org.animis.runtime.warp;
  exports org.animis.runtime.skinning;
}

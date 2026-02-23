module org.animis.runtime {
  requires org.animis;
  requires org.vectrix;
  // MeshForge currently ships as an automatic module named "meshforge".
  // If MeshForge adds module-info.java, update this to its explicit module name.
  requires meshforge;

  exports org.animis.runtime.api;
  exports org.animis.runtime.pose;
  exports org.animis.runtime.ik;
  exports org.animis.runtime.skinning;
}

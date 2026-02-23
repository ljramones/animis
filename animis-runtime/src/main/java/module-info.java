module dev.ljramones.animis.runtime {
  requires dev.ljramones.animis;
  requires org.vectrix;
  // MeshForge currently ships as an automatic module named "meshforge".
  // If MeshForge adds module-info.java, update this to its explicit module name.
  requires meshforge;

  exports dev.ljramones.animis.runtime.api;
  exports dev.ljramones.animis.runtime.pose;
  exports dev.ljramones.animis.runtime.ik;
  exports dev.ljramones.animis.runtime.skinning;
}

package org.dynamisengine.animis.retarget;

import java.util.List;

public record RetargetMap(
    List<JointMapping> mappings,
    boolean scaleTranslations
) {}

package org.animis.retarget;

import java.util.List;

public record RetargetMap(
    List<JointMapping> mappings,
    boolean scaleTranslations
) {}

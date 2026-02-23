package dev.ljramones.animis.runtime.blend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EventAccumulator {
  private final Set<String> eventNames = new LinkedHashSet<>();

  public void add(final String eventName) {
    if (eventName != null && !eventName.isEmpty()) {
      this.eventNames.add(eventName);
    }
  }

  public void addAll(final List<String> names) {
    if (names == null) {
      return;
    }
    for (final String name : names) {
      add(name);
    }
  }

  public List<String> snapshot() {
    return new ArrayList<>(this.eventNames);
  }

  public void reset() {
    this.eventNames.clear();
  }
}

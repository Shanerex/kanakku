package dev.kanakku.groups.domain;

import java.util.List;
import java.util.Optional;

/**
 * A group's cover: one of the identity colours ({@code color:mango}) or one emoji from a short
 * curated list ({@code emoji:🏖}). Anything else, such as a URL, is refused.
 */
public final class CoverPreset {

  public static final List<String> COLORS =
      List.of(
          "violet", "grape", "pink", "coral", "tangerine", "mango", "lime", "mint", "teal", "sky");

  public static final List<String> EMOJIS =
      List.of(
          "🏖", // beach
          "🏠", // house
          "💞", // revolving hearts
          "🎉", // party popper
          "👥", // people
          "✈", // plane
          "🏔", // mountain
          "🍕", // pizza
          "🛒", // cart
          "🚗", // car
          "🎂", // cake
          "⛺", // tent
          "🍻", // beers
          "🎬", // clapper
          "🏝", // island
          "🍽"); // plate

  private static final String VARIATION_SELECTOR = "️";
  private static final String COLOR = "color:";
  private static final String EMOJI = "emoji:";

  private CoverPreset() {}

  /** The canonical stored form, or empty when {@code value} is not a preset. Null is not one. */
  public static Optional<String> normalize(String value) {
    if (value == null) {
      return Optional.empty();
    }
    if (value.startsWith(COLOR) && COLORS.contains(value.substring(COLOR.length()))) {
      return Optional.of(value);
    }
    if (value.startsWith(EMOJI)) {
      String emoji = value.substring(EMOJI.length()).replace(VARIATION_SELECTOR, "");
      if (EMOJIS.contains(emoji)) {
        return Optional.of(EMOJI + emoji);
      }
    }
    return Optional.empty();
  }
}

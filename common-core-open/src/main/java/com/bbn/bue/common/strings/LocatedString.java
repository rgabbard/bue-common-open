package com.bbn.bue.common.strings;

import com.bbn.bue.common.strings.offsets.ASRTime;
import com.bbn.bue.common.strings.offsets.ByteOffset;
import com.bbn.bue.common.strings.offsets.CharOffset;
import com.bbn.bue.common.strings.offsets.EDTOffset;
import com.bbn.bue.common.strings.offsets.OffsetGroup;
import com.bbn.bue.common.strings.offsets.OffsetGroupRange;
import com.bbn.bue.common.strings.offsets.OffsetRange;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getLast;

/**
 * * Class for storing and manipulating strings that have been read in from a file, without losing
 * the relationship between each character and its origin in the file from which it was read.  In
 * particular, for each character in the located string, we record a start offset and an end offset
 * of each offset type (ByteOffset, CharOffset, EDTOffset, and ASRTime).  Start offsets and end
 * offsets are zero- indexed, and both are inclusive.  E.g., if a character in the string came from
 * a single byte at position 12, then that character's start ByteOffset and end ByteOffset will both
 * be 12.  For a character that was encoded using three bytes at positions 14, 15, and 16, the start
 * ByteOffset will be 14, and the end ByteOffset will be 16.
 *
 * In unmodified LocatedStrings, the start CharOffset for each character will be equal to its end
 * CharOffset.  However, modifications that replace substrings can result in individual characters
 * whose start and end offsets are not equal, since the offsets of the replacement characters are
 * set based on the entire range of characters in the replaced substring.
 *
 * The four offset types that are currently stored for each character are:
 *
 * - CharOffset.  More accurately, this is a unicode code point offset.
 *
 * - ByteOffset.
 *
 * - EDTOffset.  EDT offsets are similar to character offsets, except that (i) any substrings
 * starting with "<" and extending to the matching ">" are skipped when counting offsets; and (ii)
 * the character "\r" is skipped when counting offsets. Note that condition (i) is *not* always
 * identical to skipping XML/SGML tags and comments.
 *
 * - ASRTime.  The start and end time of the speech signal that corresponds to a character.
 *
 * @author originally by David A. Herman, refactored by Edward Loper; translated to Java by Ryan
 *         Gabbard
 * @author rgabbard
 */
public final class LocatedString {

  /**
   * The string represented by this LocatedString. This may or may not match the original text it
   * came from.
   */
  private final String content;
  /**
   * The offsets in the source that this LocatedString corresponds to.
   */
  private final OffsetGroupRange bounds;
  /**
   * OffsetEntrys track the relationship between offsets and indices in the LocatedString. What this
   * relationship is, exactly, can vary between different parts of a string. Examples: (a) inside an
   * XML tag, LocatedString indices and character offsets will be incremented, but EDT offsets will
   * not. (b) When text is inserted, many LocatedString offsets will correspond to the same
   * character offset.
   */
  private final ImmutableList<OffsetEntry> regions;
  private boolean hashCodeInitialized = false;
  /**
   * We cache the hash code for performance reasons.
   */
  private int hashCode = Integer.MIN_VALUE;

  private LocatedString(final String content, final List<OffsetEntry> regions,
      final OffsetGroupRange bounds) {
    this.content = checkNotNull(content);
    this.bounds = checkNotNull(bounds);
    this.regions = ImmutableList.copyOf(regions);
    checkValidity();
  }

  private void checkValidity() {
    checkArgument(!regions.isEmpty(), "LocatedString for %s with bounds %s lacks regions",
        content, bounds);
    final int boundsStartCharOffset = bounds.startInclusive().charOffset().asInt();
    final int regionsStartCharOffset = regions.get(0).startOffsetInclusive.charOffset().asInt();
    checkArgument(boundsStartCharOffset <= regionsStartCharOffset,
        "Bounds and regions have inconsistent start char offset",
        boundsStartCharOffset, regionsStartCharOffset);
    final int boundsEndCharOffset = bounds.endInclusive().charOffset().asInt();
    final int regionsEndCharOffset = getLast(regions).endOffsetInclusive.charOffset().asInt();
    checkArgument(boundsEndCharOffset <= regionsEndCharOffset,
        "Bounds and regions have inconsistent end char offset",
        boundsEndCharOffset, regionsStartCharOffset);
  }

  public String text() {
    return content;
  }

  public int length() {
    return content.length();
  }

  /**
   * ******************************************************************
   * Offset accessors
   * *******************************************************************
   */
  public EDTOffset startEDTOffset() {
    return bounds.startInclusive().edtOffset();
  }

  public EDTOffset endEDTOffset() {
    return bounds.endInclusive().edtOffset();
  }

  public CharOffset startCharOffset() {
    return bounds.startInclusive().charOffset();
  }

  public CharOffset endCharOffset() {
    return bounds.endInclusive().charOffset();
  }

  public OffsetGroupRange bounds() {
    return bounds;
  }

  public ImmutableList<OffsetEntry> regions() {
    return regions;
  }

  /**
   * @deprecated Prefer {@link #regions()}
   */
  @Deprecated
  public List<OffsetEntry> offsetEntries() {
    return regions();
  }

  /**
   * This method computes EDT offsets and is therefore deprecated. At some point it may be removed.
   *
   * @deprecated Prefer {@link #fromStringStartingAtZero(String)}
   */
  @Deprecated
  public static LocatedString forString(final String text) {
    final OffsetGroup initialOffsets =
        OffsetGroup.from(ByteOffset.asByteOffset(0), CharOffset.asCharOffset(0),
        EDTOffset.asEDTOffset(0));
    return forString(text, initialOffsets);
  }


  public static LocatedString forString(final String text, final OffsetGroupRange bounds,
      final List<OffsetEntry> spanOffsets) {
    return new LocatedString(text, spanOffsets, bounds);
  }

  /**
   * This method computes EDT offsets and is therefore deprecated. At some point it may be removed.
   *
   * @deprecated Prefer {@link #fromStringStartingAt(String, OffsetGroup)}
   */
  @Deprecated
  public static LocatedString forString(final String text, final OffsetGroup initialOffsets) {
    return forString(text, initialOffsets, false);
  }

  /**
   * This method may compute EDT offsets and is therefore deprecated. At some point it may be
   * removed.
   *
   * @deprecated Prefer {@link #fromStringStartingAt(String, OffsetGroup)}
   */
  @Deprecated
  public static LocatedString forString(final String text, final OffsetGroup initialOffsets,
      final boolean EDTOffsetsAreCharOffsets) {
    final List<OffsetEntry> offsets =
        calculateOffsets(text, initialOffsets, EDTOffsetsAreCharOffsets);
    final OffsetGroupRange bounds = boundsFromOffsets(offsets);
    return new LocatedString(text, offsets, bounds);
  }

  @SuppressWarnings("unchecked")
  public static LocatedString fromStringStartingAtZero(final String text) {
    return forString(text, OffsetGroup.fromMatchingCharAndEDT(0), true);
  }

  @SuppressWarnings("unchecked")
  public static LocatedString fromStringStartingAt(final String text,
      final OffsetGroup initialOffsets) {
    return forString(text, initialOffsets, true);
  }

  /**
   * Return a LocatedString substring of this string.
   *
   * NOTE: Because it recomputes the various offsets of every character in the
   * substring, this method is *significantly* more expensive than just
   * fetching the String content of the substring.  If you just need the String
   * content, you should use rawSubstring() instead.
   */
  public LocatedString substring(final OffsetGroup start, final OffsetGroup end) {
    return substring(start.charOffset(), end.charOffset());
  }

  /**
   * Return a LocatedString substring of this string.
   *
   * NOTE: Because it recomputes the various offsets of every character in the
   * substring, this method is *significantly* more expensive than just
   * fetching the String content of the substring.  If you just need the String
   * content, you should use rawSubstring() instead.
   */
  public LocatedString substring(final CharOffset start, final CharOffset end) {
    final int startOffset = start.asInt() - bounds.startInclusive().charOffset().asInt();
    final int endOffset = end.asInt() - bounds.startInclusive().charOffset().asInt() + 1;

    return substring(startOffset, endOffset);
  }

  /**
   * Return a LocatedString substring of this string covering the indicated character offsets, where
   * both bounds are inclusive.
   *
   * NOTE: Because it recomputes the various offsets of every character in the
   * substring, this method is *significantly* more expensive than just
   * fetching the String content of the substring.  If you just need the String
   * content, you should use rawSubstring() instead.
   */
  public LocatedString substring(final OffsetRange<CharOffset> characterOffsetsInclusive) {
    return substring(characterOffsetsInclusive.startInclusive(), characterOffsetsInclusive.endInclusive());
  }

  /**
   * Return a LocatedString substring of this string.
   *
   * NOTE: Because it recomputes the various offsets of every character in the
   * substring, this method is *significantly* more expensive than just
   * fetching the String content of the substring.  If you just need the String
   * content, you should use rawSubstring() instead.
   */
  public LocatedString substring(final int startIndexInclusive, final int endIndexExclusive) {
    final String text = content.substring(startIndexInclusive, endIndexExclusive);
    final List<OffsetEntry> offsets = offsetsOfSubstring(startIndexInclusive, endIndexExclusive);
    final OffsetGroupRange bounds = boundsFromOffsets(offsets);
    return new LocatedString(text, offsets, bounds);
  }

  /**
   * @deprecated Prefer the more explicit {@link #rawSubstringByCharOffsets(CharOffset, CharOffset)}
   */
  @Deprecated
  public String rawSubstring(final OffsetGroup start, final OffsetGroup end) {
    return rawSubstring(start.charOffset(), end.charOffset());
  }

  /**
   * @deprecated Prefer the more explicit {@link #rawSubstringByCharOffsets(CharOffset, CharOffset)}
   */
  @Deprecated
  public String rawSubstring(final CharOffset start, final CharOffset end) {
    return rawSubstringByCharOffsets(start, end);
  }

  public String rawSubstringByCharOffsets(final CharOffset start, final CharOffset end) {
    final int startOffset = start.asInt() - bounds.startInclusive().charOffset().asInt();
    final int endOffset = end.asInt() - bounds.startInclusive().charOffset().asInt() + 1;

    return rawSubstring(startOffset, endOffset);
  }

  /**
   * Return a String substring of this string.
   */
  public String rawSubstring(final int startIndexInclusive, final int endIndexExclusive) {
    return content.substring(startIndexInclusive, endIndexExclusive);
  }

  /**
   * Returns the earliest offset group within this {@code LocatedString} whose character offset
   * matches the one supplied. If not such offset group exists, throws a {@link
   * NoSuchElementException}.
   */
  public OffsetGroup offsetGroupForCharOffset(final CharOffset offset) {
    // if this ever slows us down significantly, we can binary search
    for (final OffsetEntry entry : regions) {
      final int entryStartCharOffset = entry.startOffsetInclusive.charOffset().asInt();
      final int entryEndCharOffset = entry.endOffsetInclusive.charOffset().asInt();

      if (entryStartCharOffset <= offset.asInt() && entryEndCharOffset > offset.asInt()) {
        // we assume EDT offsets are continuous within entries
        final int offsetWithinEntry = offset.asInt() - entryStartCharOffset;

        return OffsetGroup.from(offset, EDTOffset
            .asEDTOffset(entry.startOffsetInclusive.edtOffset().asInt()
                // edt offsets are not incremented in an EDT skip region
                + (entry.isEDTSkipRegion() ? 0 : offsetWithinEntry)));
      }
    }
    throw new NoSuchElementException();
  }

  public boolean contains(LocatedString other) {
    // TODO: we do it this way because the C++ is implemented this way,
    // so implementing isSubstringOf is an easy, less error-prone
    // translation. But .contains() is more idiomatic Java.
    return other.isSubstringOf(this);
  }

  /**
   * finds the position of the first offset entry of this object which has an identical char offset to oe
   *
   * preserves the CPP interface, more or less
   */
  private int positionOfStartOffsetChar(final CharOffset charOffset) {
    for(final OffsetEntry it: offsetEntries()) {
      if(it.startOffset().charOffset().asInt() > charOffset.asInt()) {
        return -1;
      }
      if(charOffset.asInt() <= it.endOffset().charOffset().asInt()) {
        return it.startPosInclusive() + (charOffset.asInt() - it.startOffset().charOffset()
            .asInt());
      }
    }
    return -1;
  }

  private CharOffset getStartOffset(int pos) {
    final OffsetEntry oe = offsetEntries().get(lastEntryStartingBefore(pos));
    checkArgument(pos >= oe.startPosInclusive() && pos <= oe.endPosExclusive() - 1);
    if (pos == oe.startPosInclusive()) {
      return oe.startOffset().charOffset();
    } else {
      return CharOffset
          .asCharOffset(oe.startOffset().charOffset().asInt() + (pos - oe.startPosInclusive()));
    }
  }

  private CharOffset getEndOffset(int pos) {
    final OffsetEntry oe = offsetEntries().get(lastEntryStartingBefore(pos));
    checkArgument(pos >= oe.startPosInclusive() && pos <= oe.endPosExclusive());
    if (pos == oe.endPosExclusive() - 1) {
      return oe.endOffset().charOffset();
    } else {
      return CharOffset
          .asCharOffset(oe.startOffset().charOffset().asInt() + (pos - oe.startPosInclusive()));
    }
  }

  private boolean isSubstringOf(LocatedString sup) {
    final int superStringStartPos =
        sup.positionOfStartOffsetChar(offsetEntries().get(0).startOffset().charOffset());
    if (superStringStartPos < 0) {
      return false;
    }
    if (superStringStartPos + length() > sup.length()) {
      return false;
    }

    final OffsetRange<CharOffset> thisCharOffsets = this.bounds().asCharOffsetRange();
    if (thisCharOffsets.startInclusive().asInt() != sup.getStartOffset(superStringStartPos).asInt()) {
      return false;
    }
    if (thisCharOffsets.endInclusive().asInt() != sup.getEndOffset(superStringStartPos + this.length()).asInt()-1) {
      return false;
    }
    //TODO: if this is slow, do a point by point comparison instead of substring
    if (!sup.content.substring(superStringStartPos, superStringStartPos + this.length()).equals(
        content)) {
      return false;
    }
    return true;
  }

  /**
   * *****************************************************************************
   * Private implementation
   */

  @Override
  public int hashCode() {
    if (!hashCodeInitialized) {
      hashCode = Objects.hashCode(content, bounds, regions);
      hashCodeInitialized = true;
    }
    return hashCode;
  }

  @Override
  /**
   * Equality for this is quite strict - it must be exactly the same string and offsets
   * with exactly the same interior material omitted, if any.
   */
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final LocatedString other = (LocatedString) obj;

    if (hashCode() != other.hashCode()) {
      return false;
    }

    return Objects.equal(this.bounds, other.bounds) && Objects.equal(this.content, other.content)
        && Objects.equal(this.regions, other.regions);
  }

  public static final class OffsetEntry {

    private final int startInclusivePos;
    private final int endExclusivePos;
    private final OffsetGroup startOffsetInclusive;
    private final OffsetGroup endOffsetInclusive;

    public OffsetEntry(final int startPosInclusive, final int endPosExclusive,
        final OffsetGroup startOffset,
        final OffsetGroup endOffsetInclusive) {
      this.startInclusivePos = startPosInclusive;
      this.endExclusivePos = endPosExclusive;
      this.startOffsetInclusive = startOffset;
      this.endOffsetInclusive = endOffsetInclusive;
      checkArgument(endExclusivePos > startInclusivePos);
      checkArgument(endOffsetInclusive.charOffset().asInt()
          >= startOffsetInclusive.charOffset().asInt());
    }

    public int startPosInclusive() {
      return startInclusivePos;
    }

    public int endPosExclusive() {
      return endExclusivePos;
    }

    public OffsetGroup startOffset() {
      return startOffsetInclusive;
    }

    public OffsetGroup endOffset() {
      return endOffsetInclusive;
    }

    public boolean isEDTSkipRegion() {
      return charLength() > 0 && startOffset().edtOffset().equals(endOffset().edtOffset());
    }

    public final int posLength() {
      return endExclusivePos - startInclusivePos;
    }

    public final int charLength() {
      // +1 because offsets are inclusive
      return endOffset().charOffset().asInt() - startOffset().charOffset().asInt() + 1;
    }

    public final int edtLength() {
      // +1 because offsets are inclusive
      return endOffset().edtOffset().asInt() - startOffset().edtOffset().asInt() + 1;
    }

    @Override
    public String toString() {
      return "OffsetEntry{pos: [" + startInclusivePos + ", " + endExclusivePos + "]; "
          + OffsetGroupRange.from(startOffsetInclusive, endOffsetInclusive)
          + "}";
    }

    @Override
    public int hashCode() {
      return Objects
          .hashCode(startInclusivePos, endExclusivePos, startOffsetInclusive, endOffsetInclusive);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      final OffsetEntry other = (OffsetEntry) obj;
      return Objects.equal(this.startInclusivePos, other.startInclusivePos) && Objects
          .equal(this.endExclusivePos, other.endExclusivePos) && Objects
          .equal(this.startOffsetInclusive, other.startOffsetInclusive)
          && Objects.equal(this.endOffsetInclusive, other.endOffsetInclusive);
    }
  }

  @Deprecated
  private static List<OffsetEntry> calculateOffsets(final String text,
      final OffsetGroup initialOffsets, final boolean EDTOffsetsAreCharOffsets) {
    checkNotNull(text);
    checkNotNull(initialOffsets);

    final ImmutableList.Builder<OffsetEntry> offsets = ImmutableList.builder();

    final Optional<ASRTime> weDontKnowASRTime = Optional.absent();
    int inTag = 0;
    boolean useByteOffsets = initialOffsets.byteOffset().isPresent();
    int byteOffset = useByteOffsets ? initialOffsets.byteOffset().get().asInt() : Integer.MIN_VALUE;
    int charOffset = initialOffsets.charOffset().asInt();
    int edtOffset = initialOffsets.edtOffset().asInt();

    int pos = 0;
    int startPos = 0;
    boolean justLeftXMLTag = false;
    char prevChar = 0;
    OffsetGroup start = initialOffsets;

    // TODO: figure out how this works with UTF-16 unicode encoding...
    for (; pos < text.length(); ++pos) {
      final char c = text.charAt(pos);
      if (!EDTOffsetsAreCharOffsets && pos > 0 &&
          (inTag == 0 && (c == '<' || prevChar == '\r') || justLeftXMLTag)
          && !(justLeftXMLTag && c == '<')) {
        final int prevEDTOffset =
            (edtOffset == 0 || prevChar == '\r') ? edtOffset : (edtOffset - 1);
        offsets.add(
            new OffsetEntry(startPos, pos, start,
                OffsetGroup.from(useByteOffsets ? ByteOffset.asByteOffset(byteOffset - 1) : null,
                    CharOffset.asCharOffset(charOffset - 1),
                    EDTOffset.asEDTOffset(prevEDTOffset))));
        startPos = pos;
        final int startEDTOffset = (c == '<') ? edtOffset - 1 : edtOffset;
        start = OffsetGroup
            .from(useByteOffsets ? ByteOffset.asByteOffset(byteOffset) : null, CharOffset.asCharOffset(charOffset),
            EDTOffset.asEDTOffset(startEDTOffset));
      }

      ++charOffset;
      byteOffset += UTF8BytesInChar(c);
      if (EDTOffsetsAreCharOffsets || (!(inTag != 0 || c == '<' || c == '\r'))) {
        ++edtOffset;
      }
      if (!EDTOffsetsAreCharOffsets) {
        justLeftXMLTag = false;
        if (c == '<') {
          ++inTag;
        } else if (inTag > 0 && c == '>') {
          --inTag;
          if (inTag == 0) {
            justLeftXMLTag = true;
          }
        }
      }
      prevChar = c;
    }
    if (pos > startPos) {
      final int prevEDTOffset = Math.max(start.edtOffset().asInt(), edtOffset - 1);
      offsets.add(new OffsetEntry(startPos, pos, start,
          OffsetGroup.from(useByteOffsets ? ByteOffset.asByteOffset(byteOffset - 1) : null,
              CharOffset.asCharOffset(charOffset - 1),
              EDTOffset.asEDTOffset(prevEDTOffset))));
    }
    return offsets.build();
  }

  private static OffsetGroupRange boundsFromOffsets(final List<OffsetEntry> offsets) {
    checkArgument(!offsets.isEmpty());
    return OffsetGroupRange
        .from(offsets.get(0).startOffsetInclusive,
            offsets.get(offsets.size() - 1).endOffsetInclusive);
  }

  private static final char ONE_BYTE = 0x007f;
  private static final char TWO_BYTE = 0x07ff;
  private static final char THREE_BYTE = 0xffff;

  private static final int UTF8BytesInChar(final char c) {
    if (c <= ONE_BYTE) {
      return 1;
    } else if (c <= TWO_BYTE) {
      return 2;
    } else if (c <= THREE_BYTE) {
      return 3;
    } else {
      return 4;
    }
  }

  /**
   * Returns offsets corresponding to substring, in order.
   */
  private List<OffsetEntry> offsetsOfSubstring(final int substringStartIndexInclusive,
      final int substringEndIndexExclusive) {
    checkArgument(substringStartIndexInclusive >= 0);
    checkArgument(substringEndIndexExclusive <= length());
    checkArgument(substringStartIndexInclusive < substringEndIndexExclusive,
        "Start Index %s not less than end index %s", substringStartIndexInclusive,
        substringEndIndexExclusive);

    final ImmutableList.Builder<OffsetEntry> ret = ImmutableList.builder();

    // recall that a LocatedString tracks offsets using a sequence of "offset entries".  Each of
    // these entries is either a region where the EDT and char offsets have the same length
    // (indicating nothing is skipped for EDT in this region) or it is an "EDT skip region" where
    // char offsets continue to grow but EDT offsets do not
    //     To make a new substring, we need to compute its offset entries.
    for (int entryNum = lastEntryStartingBefore(substringStartIndexInclusive);
         entryNum < regions.size(); ++entryNum) {
      final OffsetEntry entry = regions.get(entryNum);
      // sanity check
      checkState(entry.startInclusivePos < substringEndIndexExclusive);

      // this will be negative if the requested substring starts in the middle of the entry
      // positive indicates the entry starts after the requested substring start
      final int entryStartRelativeToSubstringStart =
          entry.startInclusivePos - substringStartIndexInclusive;

      // if the entry starts before the substring, the earlier part of the entry will get cut off,
      // hence the max(0, ...)
      final int newStartPosInclusive = Math.max(0, entryStartRelativeToSubstringStart);
      final int newEndPosExclusive = Math.min(
          substringEndIndexExclusive - substringStartIndexInclusive,
          entry.endExclusivePos - substringStartIndexInclusive);

      // if anything was chopped off the entry beginning we need to alter the starting bounds
      final int numPositionsRemovedFromEntryBeginning =
          Math.max(0, -entryStartRelativeToSubstringStart);
      OffsetGroup newStartOffsetInclusive =
          shiftOffsetGroup(entry.startOffset(), numPositionsRemovedFromEntryBeginning,
              entry.isEDTSkipRegion());

      // if anything was chopped off the end we need to alter the ending bounds
      final int numPositionsRemovedFromEntryEnd =
          Math.max(0, entry.endExclusivePos - substringEndIndexExclusive);
      OffsetGroup newEndOffsetInclusive =
          shiftOffsetGroup(entry.endOffset(), -numPositionsRemovedFromEntryEnd,
              entry.isEDTSkipRegion());

      ret.add(new OffsetEntry(newStartPosInclusive, newEndPosExclusive, newStartOffsetInclusive,
          newEndOffsetInclusive));

      final int requestedSubstringLength =
          substringEndIndexExclusive - substringStartIndexInclusive;
      if (newEndPosExclusive >= requestedSubstringLength) {
        break;
      }
    }

    return ret.build();
  }

  // shifts an OffsetEntry boundary the specified amount, taking into account whether or not
  // it is an EDT skip entry. Used by offsetsOfSubstring
  private static OffsetGroup shiftOffsetGroup(OffsetGroup entryBoundary, int shift,
      boolean isEDTSkipRegion) {
    if (shift == 0) {
      // save a little memory by reusing the immutable OffsetGroup object
      // if we aren't actually going to change it
      return entryBoundary;
    }

    final CharOffset newCharOffsetValue = CharOffset.asCharOffset(
        entryBoundary.charOffset().asInt() + shift);
    final EDTOffset newEDTOffsetValue;
    if (!isEDTSkipRegion) {
      newEDTOffsetValue = EDTOffset.asEDTOffset(entryBoundary.edtOffset().asInt() + shift);
    } else {
      // if it was an EDT skip entry, the EDT counts were not being incremented within
      // this entry anyway, so they don't need adjusting
      newEDTOffsetValue = EDTOffset.asEDTOffset(entryBoundary.edtOffset().asInt());
    }
    return OffsetGroup.from(newCharOffsetValue, newEDTOffsetValue);
  }

  private int lastEntryStartingBefore(final int pos) {
    int i = 1;
    while (i < regions.size() && regions.get(i).startInclusivePos <= pos) {
      ++i;
    }
    return i - 1;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("bounds", bounds).add("content", content).toString();
  }


}

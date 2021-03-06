package com.bbn.bue.common.evaluation;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static com.bbn.bue.common.evaluation.EvaluationConstants.PRESENT;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * If we have some metric broken down by categories which results in confusion matrices, these
 * will print bootstrapped precision/recall/F-measure scores.
 *
 * Each of these is provided with an output directory (<tt>outputDir</tt>) and a name. The
 * following outputs will be written:
 * <ul>
 *   <li>{@code outputDir/name.bootstrapped.txt}: A human readable chart with the bootstrapped
 *   percentiles for precision, recall, F1, and accuracy.</li>
 *   <li>{@code outputDir/name.bootstrapped.csv}: the same information in a machine-friendly
 *   CSV form.</li>
 *   <li>{@code outputDir/name.bootstrapped.medians.csv}: CSV for the median bootstrapped
 *   values only.</li>
 *   <li>{@code outputDir/name.bootstrapped.raw}: Machine-consumable list of all the
 *   bootstrap samples for all four metrics.  This is useful for passing to gnuplot to
 *   make box and whisker graphs.</li>
 * </ul>
 */
public final class BrokenDownPRFAggregator
    implements BootstrapInspector.SummaryAggregator<Map<String, SummaryConfusionMatrix>> {
  // BrokenDownFMeasureAggregator does all the work
  private final BootstrapInspector.SummaryAggregator<Map<String, FMeasureCounts>> innerAggregator;

  private BrokenDownPRFAggregator(
      final BootstrapInspector.SummaryAggregator<Map<String, FMeasureCounts>> innerAggregator) {
    this.innerAggregator = checkNotNull(innerAggregator);
  }

  public static BrokenDownPRFAggregator create(String name, File outputDir) {
    return new BrokenDownPRFAggregator(BrokenDownFMeasureAggregator.create(name, outputDir));
  }

  @Override
  public void observeSample(
      final Collection<Map<String, SummaryConfusionMatrix>> observationSummaries) {
    innerAggregator.observeSample(Collections2.transform(observationSummaries,
        new Function<Map<String, SummaryConfusionMatrix>, Map<String, FMeasureCounts>>() {
          @Override
          public Map<String, FMeasureCounts> apply(final Map<String, SummaryConfusionMatrix> x) {
            checkNotNull(x);
            final ImmutableMap.Builder<String, FMeasureCounts> ret = ImmutableMap.builder();
            for (final Map.Entry<String, SummaryConfusionMatrix> entry : x.entrySet()) {
              ret.put(entry.getKey(), SummaryConfusionMatrices.FMeasureVsAllOthers(entry.getValue(), PRESENT));
            }
            return ret.build();
          }
        }));
  }

  @Override
  public void finish() throws IOException {
    innerAggregator.finish();
  }
}


package mil.nga.giat.geowave.analytics.kmeans.mapreduce.runners;

import java.util.Set;
import java.util.UUID;

import mil.nga.giat.geowave.analytics.clustering.ClusteringUtils;
import mil.nga.giat.geowave.analytics.clustering.runners.ClusteringRunner;
import mil.nga.giat.geowave.analytics.distance.FeatureCentroidDistanceFn;
import mil.nga.giat.geowave.analytics.extract.DimensionExtractor;
import mil.nga.giat.geowave.analytics.extract.SimpleFeatureCentroidExtractor;
import mil.nga.giat.geowave.analytics.extract.SimpleFeatureGeometryExtractor;
import mil.nga.giat.geowave.analytics.parameters.CentroidParameters;
import mil.nga.giat.geowave.analytics.parameters.CommonParameters;
import mil.nga.giat.geowave.analytics.parameters.GlobalParameters;
import mil.nga.giat.geowave.analytics.parameters.SampleParameters;
import mil.nga.giat.geowave.analytics.tools.PropertyManagement;
import mil.nga.giat.geowave.analytics.tools.SimpleFeatureItemWrapperFactory;
import mil.nga.giat.geowave.analytics.tools.mapreduce.MapReduceJobController;
import mil.nga.giat.geowave.analytics.tools.mapreduce.MapReduceJobRunner;
import mil.nga.giat.geowave.store.adapter.DataAdapter;
import mil.nga.giat.geowave.store.index.Index;

import org.apache.commons.cli.Option;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.opengis.feature.simple.SimpleFeature;

/**
 * The KMeans Parallel algorithm,labeled Algorithm 2 within in section 3.3 of
 * 
 * Bahmani, Kumar, Moseley, Vassilvitskii and Vattani. Scalable K-means++. VLDB
 * Endowment Vol. 5, No. 7. 2012.
 * 
 * @formatter:off Couple things to note:
 * 
 *                (1) Updating the cost of each sampled point occurs as the
 *                first step within sampling loop; the initial sample is
 *                performed outside the loop.
 * 
 *                (2) A final update cost occurs outside the sampling loop just
 *                prior to stripping off the top 'K' centers.
 * 
 * @formatter:on
 * 
 */
public class KMeansParallelJobRunner extends
		MapReduceJobController implements
		ClusteringRunner
{
	final SampleMultipleSetsJobRunner<SimpleFeature> sampleSetsRunner = new SampleMultipleSetsJobRunner<SimpleFeature>();
	final StripWeakCentroidsRunner<SimpleFeature> stripWeakCentroidsRunner = new StripWeakCentroidsRunner<SimpleFeature>();
	final KMeansIterationsJobRunner<SimpleFeature> kmeansJobRunner = new KMeansIterationsJobRunner<SimpleFeature>();

	private int currentZoomLevel = 1;
	private Path currentInputPath = null;

	public KMeansParallelJobRunner() {
		// defaults
		setZoomLevel(1);

		// sts of child runners
		init(
				new MapReduceJobRunner[] {
					sampleSetsRunner,
					stripWeakCentroidsRunner, // run this one more time with
												// 'smaller' size
					kmeansJobRunner
				},
				new PostOperationTask[] {
					DoNothingTask,
					DoNothingTask,
					new PostOperationTask() {

						@Override
						public void runTask(
								final Configuration config,
								final MapReduceJobRunner runner ) {
							kmeansJobRunner.setReducerCount(stripWeakCentroidsRunner.getCurrentCentroidCount());
						}
					},
					DoNothingTask
				});
	}

	@Override
	public void setZoomLevel(
			final int zoomLevel ) {
		currentZoomLevel = zoomLevel;
	}

	@Override
	public void setInputHDFSPath(
			final Path inputHDFSPath ) {
		currentInputPath = inputHDFSPath;
	}

	@Override
	public int run(
			final Configuration configuration,
			final PropertyManagement propertyManagement )
			throws Exception {
		return runJob(
				configuration,
				propertyManagement);
	}

	private int runJob(
			final Configuration config,
			final PropertyManagement propertyManagement )
			throws Exception {

		propertyManagement.store(
				CentroidParameters.Centroid.ZOOM_LEVEL,
				currentZoomLevel);
		propertyManagement.store(
				CommonParameters.Common.HDFS_INPUT_PATH,
				currentInputPath);
		propertyManagement.storeIfEmpty(
				GlobalParameters.Global.BATCH_ID,
				UUID.randomUUID().toString());

		propertyManagement.storeIfEmpty(
				CentroidParameters.Centroid.WRAPPER_FACTORY_CLASS,
				SimpleFeatureItemWrapperFactory.class);
		propertyManagement.storeIfEmpty(
				CommonParameters.Common.DISTANCE_FUNCTION_CLASS,
				FeatureCentroidDistanceFn.class);
		propertyManagement.storeIfEmpty(
				CentroidParameters.Centroid.EXTRACTOR_CLASS,
				SimpleFeatureCentroidExtractor.class);
		propertyManagement.storeIfEmpty(
				CommonParameters.Common.DIMENSION_EXTRACT_CLASS,
				SimpleFeatureGeometryExtractor.class);

		stripWeakCentroidsRunner.setRange(
				propertyManagement.getPropertyAsInt(
						SampleParameters.Sample.MIN_SAMPLE_SIZE,
						2),
				propertyManagement.getPropertyAsInt(
						SampleParameters.Sample.MAX_SAMPLE_SIZE,
						1000));

		createAdapter(propertyManagement);
		createIndex(propertyManagement);

		return super.run(
				config,
				propertyManagement);
	}

	@Override
	public void fillOptions(
			Set<Option> options ) {
		kmeansJobRunner.fillOptions(options);
		sampleSetsRunner.fillOptions(options);
		// while override
		PropertyManagement.removeOption(
				options,
				CommonParameters.Common.HDFS_INPUT_PATH);
		PropertyManagement.removeOption(
				options,
				CentroidParameters.Centroid.ZOOM_LEVEL);
	}

	private Index createIndex(
			final PropertyManagement propertyManagement )
			throws Exception {
		return ClusteringUtils.createIndex(
				propertyManagement.getProperty(CentroidParameters.Centroid.INDEX_ID),
				propertyManagement.getProperty(GlobalParameters.Global.ZOOKEEKER),
				propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_INSTANCE),
				propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_USER),
				propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_PASSWORD),
				propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_NAMESPACE));

	}

	private DataAdapter<?> createAdapter(
			final PropertyManagement propertyManagement )
			throws Exception {
		return ClusteringUtils.createAdapter(
				propertyManagement.getProperty(CentroidParameters.Centroid.DATA_TYPE_ID),
				propertyManagement.getProperty(GlobalParameters.Global.ZOOKEEKER),
				propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_INSTANCE),
				propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_USER),
				propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_PASSWORD),
				propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_NAMESPACE),
				propertyManagement.getPropertyAsClass(
						CommonParameters.Common.DIMENSION_EXTRACT_CLASS,
						DimensionExtractor.class).newInstance().getDimensionNames());

	}

}
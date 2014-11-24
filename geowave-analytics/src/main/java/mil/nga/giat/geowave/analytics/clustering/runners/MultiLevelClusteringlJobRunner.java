package mil.nga.giat.geowave.analytics.clustering.runners;

import java.util.Set;

import mil.nga.giat.geowave.analytics.extract.SimpleFeatureCentroidExtractor;
import mil.nga.giat.geowave.analytics.extract.SimpleFeatureGeometryExtractor;
import mil.nga.giat.geowave.analytics.parameters.CentroidParameters;
import mil.nga.giat.geowave.analytics.parameters.ClusteringParameters;
import mil.nga.giat.geowave.analytics.parameters.ClusteringParameters.Clustering;
import mil.nga.giat.geowave.analytics.parameters.CommonParameters;
import mil.nga.giat.geowave.analytics.parameters.ExtractParameters;
import mil.nga.giat.geowave.analytics.parameters.GlobalParameters;
import mil.nga.giat.geowave.analytics.parameters.GlobalParameters.Global;
import mil.nga.giat.geowave.analytics.parameters.HullParameters;
import mil.nga.giat.geowave.analytics.tools.PropertyManagement;
import mil.nga.giat.geowave.analytics.tools.mapreduce.MapReduceJobController;
import mil.nga.giat.geowave.analytics.tools.mapreduce.MapReduceJobRunner;
import mil.nga.giat.geowave.store.index.IndexType;

import org.apache.commons.cli.Option;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 * Runs a clustering at multiple levels. Lower levels cluster within each
 * cluster of the higher level.
 * 
 * Steps:
 * 
 * @formatter: off
 * 
 *             (1) Extract and deduplicate items from geowave.
 * 
 *             (2) Cluster item within their assigned groups. Initially, items
 *             are all part of the same group.
 * 
 *             (3) Assign to each point the cluster (group id).
 * 
 *             (4) Repeat steps 2 to 3 for each lower level.
 * 
 * @formatter: on
 * 
 */
public abstract class MultiLevelClusteringlJobRunner extends
		MapReduceJobController implements
		MapReduceJobRunner
{

	final GroupAssigmentJobRunner groupAssignmentRunner = new GroupAssigmentJobRunner();
	final GeoWaveAnalyticExtractJobRunner jobExtractRunner = new GeoWaveAnalyticExtractJobRunner();
	final ConvexHullJobRunner hullRunner = new ConvexHullJobRunner();

	public MultiLevelClusteringlJobRunner() {
		init(
				new MapReduceJobRunner[] {},
				new PostOperationTask[] {});
	}

	protected abstract ClusteringRunner getClusteringRunner();

	@Override
	public void fillOptions(
			final Set<Option> options ) {

		jobExtractRunner.fillOptions(options);
		hullRunner.fillOptions(options);
		getClusteringRunner().fillOptions(
				options);
		ClusteringParameters.fillOptions(
				options,
				new Clustering[] {
					Clustering.ZOOM_LEVELS
				});
		GlobalParameters.fillOptions(
				options,
				new Global[] {
					Global.BATCH_ID,
					Global.HDFS_BASEDIR,
					Global.ACCUMULO_NAMESPACE
				});
		// the output data type is used for centroid management
		PropertyManagement.removeOption(
				options,
				CentroidParameters.Centroid.DATA_TYPE_ID);
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

		final ClusteringRunner clusteringRunner = getClusteringRunner();
		final Integer zoomLevels = propertyManagement.getPropertyAsInt(
				Clustering.ZOOM_LEVELS,
				1);

		jobExtractRunner.setConf(config);

		final String dataTypeId = propertyManagement.getProperty(
				ExtractParameters.Extract.OUTPUT_DATA_TYPE_ID,
				"centroid");
		propertyManagement.storeIfEmpty(
				ExtractParameters.Extract.OUTPUT_DATA_TYPE_ID,
				dataTypeId);

		propertyManagement.storeIfEmpty(
				CentroidParameters.Centroid.EXTRACTOR_CLASS,
				SimpleFeatureCentroidExtractor.class);

		propertyManagement.storeIfEmpty(
				CommonParameters.Common.DIMENSION_EXTRACT_CLASS,
				SimpleFeatureGeometryExtractor.class);

		propertyManagement.store(
				CentroidParameters.Centroid.DATA_TYPE_ID,
				dataTypeId);

		// TODO: set out index type for extracts?
		propertyManagement.storeIfEmpty(
				CentroidParameters.Centroid.INDEX_ID,
				IndexType.SPATIAL_VECTOR.getDefaultId());

		propertyManagement.storeIfEmpty(
				HullParameters.Hull.INDEX_ID,
				IndexType.SPATIAL_VECTOR.getDefaultId());

		final FileSystem fs = FileSystem.get(config);

		final Path extractPath = GeoWaveAnalyticExtractJobRunner.getHdfsOutputPath(propertyManagement);

		if (fs.exists(extractPath)) {
			fs.delete(
					extractPath,
					true);
		}

		// first. extract data
		int status = jobExtractRunner.run(
				config,
				propertyManagement);

		// retain the output path
		propertyManagement.store(
				CommonParameters.Common.HDFS_INPUT_PATH,
				extractPath);

		groupAssignmentRunner.setInputHDFSPath(extractPath);
		clusteringRunner.setInputHDFSPath(extractPath);
		hullRunner.setInputHDFSPath(extractPath);

		final boolean retainGroupAssigments = propertyManagement.getPropertyAsBoolean(
				Clustering.RETAIN_GROUP_ASSIGNMENTS,
				false);

		// run clustering for each level
		final String outputBaseDir = propertyManagement.getProperty(
				GlobalParameters.Global.HDFS_BASEDIR,
				"/tmp");
		for (int i = 0; (status == 0) && (i < zoomLevels); i++) {
			final int zoomLevel = i + 1;
			clusteringRunner.setZoomLevel(zoomLevel);
			status = clusteringRunner.run(
					config,
					propertyManagement);
			if (status == 0) {
				final Path nextPath = new Path(
						outputBaseDir + "/" + propertyManagement.getProperty(GlobalParameters.Global.ACCUMULO_NAMESPACE) + "_level_" + zoomLevel);
				if (fs.exists(nextPath)) {
					fs.delete(
							nextPath,
							true);
				}

				groupAssignmentRunner.setOutputHDFSPath(nextPath);
				groupAssignmentRunner.setZoomLevel(zoomLevel);

				status = retainGroupAssigments ? groupAssignmentRunner.run(
						config,
						propertyManagement) : 0;

				if (status == 0) {
					status = hullRunner.run(
							config,
							propertyManagement);
				}
				if (retainGroupAssigments) clusteringRunner.setInputHDFSPath(nextPath);
				groupAssignmentRunner.setInputHDFSPath(nextPath);

			}
		}
		return status;
	}
}
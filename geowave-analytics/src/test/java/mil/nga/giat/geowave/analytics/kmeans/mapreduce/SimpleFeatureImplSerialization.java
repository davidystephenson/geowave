package mil.nga.giat.geowave.analytics.kmeans.mapreduce;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import mil.nga.giat.geowave.vector.adapter.FeatureWritable;

import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.Serialization;
import org.apache.hadoop.io.serializer.Serializer;
import org.geotools.feature.simple.SimpleFeatureImpl;

public class SimpleFeatureImplSerialization implements
		Serialization<SimpleFeatureImpl>
{

	@Override
	public boolean accept(
			final Class<?> c ) {
		return SimpleFeatureImpl.class.isAssignableFrom(c);
	}

	@Override
	public Deserializer<SimpleFeatureImpl> getDeserializer(
			final Class<SimpleFeatureImpl> arg0 ) {
		return new SFDeserializer();
	}

	@Override
	public Serializer<SimpleFeatureImpl> getSerializer(
			final Class<SimpleFeatureImpl> arg0 ) {
		return new SFSerializer();
	}

	public class SFDeserializer implements
			Deserializer<SimpleFeatureImpl>
	{

		private InputStream in;
		private DataInputStream dataInput;

		@Override
		public void open(
				final InputStream in )
				throws IOException {
			this.in = in;
			dataInput = new DataInputStream(
					in);
		}

		@Override
		public SimpleFeatureImpl deserialize(
				final SimpleFeatureImpl t )
				throws IOException {
			final FeatureWritable fw = new FeatureWritable();
			fw.readFields(dataInput);
			return (SimpleFeatureImpl) fw.getFeature();
		}

		@Override
		public void close()
				throws IOException {
			in.close();
		}

	}

	private static class SFSerializer implements
			Serializer<SimpleFeatureImpl>
	{

		private OutputStream out;
		private DataOutput dataOutput;

		@Override
		public void open(
				final OutputStream out )
				throws IOException {
			this.out = out;
			dataOutput = new DataOutputStream(
					out);
		}

		@Override
		public void serialize(
				final SimpleFeatureImpl t )
				throws IOException {
			final FeatureWritable fw = new FeatureWritable(
					t.getFeatureType(),
					t);

			fw.write(dataOutput);
		}

		@Override
		public void close()
				throws IOException {
			out.close();
		}
	}

}

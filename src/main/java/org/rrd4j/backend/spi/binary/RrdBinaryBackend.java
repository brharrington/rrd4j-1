package org.rrd4j.backend.spi.binary;

import java.io.IOException;

import org.rrd4j.backend.RrdBackend;
import org.rrd4j.backend.spi.ArcState;
import org.rrd4j.backend.spi.Archive;
import org.rrd4j.backend.spi.Datasource;
import org.rrd4j.backend.spi.Header;
import org.rrd4j.backend.spi.Robin;
import org.rrd4j.core.ArcDef;
import org.rrd4j.core.DataImporter;
import org.rrd4j.core.DsDef;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;

public abstract class RrdBinaryBackend extends RrdBackend implements Allocated {

    private final RrdAllocator allocator = new RrdAllocator();

    private HeaderBinary header;
    private DatasourceBinary[] datasources;
    private ArchiveBinary[] archives;
    private ArcStateBinary[][] states;
    private Robin[][] robins;

    /**
     * Creates backend for a RRD storage with the given path.
     *
     * @param path String identifying RRD storage. For files on the disk, this
     *             argument should represent file path. Other storage types might interpret
     *             this argument differently.
     */
    protected RrdBinaryBackend(String path) {
        super(path);
    }

    /**
     * Writes an array of bytes to the underlying storage starting from the given
     * storage offset.
     *
     * @param offset Storage offset.
     * @param b      Array of bytes that should be copied to the underlying storage
     * @throws java.io.IOException Thrown in case of I/O error
     */
    protected abstract void write(long offset, byte[] b) throws IOException;

    /**
     * Reads an array of bytes from the underlying storage starting from the given
     * storage offset.
     *
     * @param offset Storage offset.
     * @param b      Array which receives bytes from the underlying storage
     * @throws java.io.IOException Thrown in case of I/O error
     */
    protected abstract void read(long offset, byte[] b) throws IOException;

    /**
     * Returns the number of RRD bytes in the underlying storage.
     *
     * @return Number of RRD bytes in the storage.
     * @throws java.io.IOException Thrown in case of I/O error.
     */
    public abstract long getLength() throws IOException;

    /**
     * Sets the number of bytes in the underlying RRD storage.
     * This method is called only once, immediately after a new RRD storage gets created.
     *
     * @param length Length of the underlying RRD storage in bytes.
     * @throws java.io.IOException Thrown in case of I/O error.
     */
    protected abstract void setLength(long length) throws IOException;

    /**
     * Closes the underlying backend.
     *
     * @throws java.io.IOException Thrown in case of I/O error
     */
    public void close() throws IOException {
        saveState();
    }

    private void saveState() throws IOException {
        header.save();
        for(Datasource ds: datasources) {
            ds.save();
        }
        for(Archive archive: archives) {
            archive.save();
        }
        for(ArcState[] s1: states) {
            for(ArcState s2: s1) {
                s2.save();
            }
        }
        for(Robin[] r1: robins) {
            for(Robin r2: r1) {
                r2.save();
            }
        }
    }

    /**
     * This method suggests the caching policy to the Rrd4j frontend (high-level) classes. If <code>true</code>
     * is returned, frontend classes will cache frequently used parts of a RRD file in memory to improve
     * performance. If </code>false</code> is returned, high level classes will never cache RRD file sections
     * in memory.
     *
     * @return <code>true</code> if file caching is enabled, <code>false</code> otherwise. By default, the
     *         method returns <code>true</code> but it can be overriden in subclasses.
     */
    protected boolean isCachingAllowed() {
        return true;
    }

    /**
     * Reads all RRD bytes from the underlying storage.
     *
     * @return RRD bytes
     * @throws java.io.IOException Thrown in case of I/O error
     */
    public final byte[] readAll() throws IOException {
        byte[] b = new byte[(int) getLength()];
        read(0, b);
        return b;
    }

    final void writeInt(long offset, int value) throws IOException {
        write(offset, getIntBytes(value));
    }

    final void writeLong(long offset, long value) throws IOException {
        write(offset, getLongBytes(value));
    }

    final void writeDouble(long offset, double value) throws IOException {
        write(offset, getDoubleBytes(value));
    }

    final void writeDouble(long offset, double value, int count) throws IOException {
        byte[] b = getDoubleBytes(value);
        byte[] image = new byte[8 * count];
        for (int i = 0, k = 0; i < count; i++) {
            image[k++] = b[0];
            image[k++] = b[1];
            image[k++] = b[2];
            image[k++] = b[3];
            image[k++] = b[4];
            image[k++] = b[5];
            image[k++] = b[6];
            image[k++] = b[7];
        }
        write(offset, image);
        image = null;
    }

    final void writeDouble(long offset, double[] values) throws IOException {
        int count = values.length;
        byte[] image = new byte[8 * count];
        for (int i = 0, k = 0; i < count; i++) {
            byte[] b = getDoubleBytes(values[i]);
            image[k++] = b[0];
            image[k++] = b[1];
            image[k++] = b[2];
            image[k++] = b[3];
            image[k++] = b[4];
            image[k++] = b[5];
            image[k++] = b[6];
            image[k++] = b[7];
        }
        write(offset, image);
        image = null;
    }

    final void writeString(long offset, String value) throws IOException {
        value = value.trim();
        byte[] b = new byte[RrdPrimitive.STRING_LENGTH * 2];
        for (int i = 0, k = 0; i < RrdPrimitive.STRING_LENGTH; i++) {
            char c = (i < value.length()) ? value.charAt(i) : ' ';
            byte[] cb = getCharBytes(c);
            b[k++] = cb[0];
            b[k++] = cb[1];
        }
        write(offset, b);
    }

    final int readInt(long offset) throws IOException {
        byte[] b = new byte[4];
        read(offset, b);
        return getInt(b);
    }

    final long readLong(long offset) throws IOException {
        byte[] b = new byte[8];
        read(offset, b);
        return getLong(b);
    }

    final double readDouble(long offset) throws IOException {
        byte[] b = new byte[8];
        read(offset, b);
        return getDouble(b);
    }

    final double[] readDouble(long offset, int count) throws IOException {
        int byteCount = 8 * count;
        byte[] image = new byte[byteCount];
        read(offset, image);
        double[] values = new double[count];
        for (int i = 0, k = -1; i < count; i++) {
            byte[] b = new byte[]{
                    image[++k], image[++k], image[++k], image[++k],
                    image[++k], image[++k], image[++k], image[++k]
            };
            values[i] = getDouble(b);
        }
        image = null;
        return values;
    }

    final String readString(long offset) throws IOException {
        byte[] b = new byte[RrdPrimitive.STRING_LENGTH * 2];
        char[] c = new char[RrdPrimitive.STRING_LENGTH];
        read(offset, b);
        for (int i = 0, k = -1; i < RrdPrimitive.STRING_LENGTH; i++) {
            byte[] cb = new byte[]{b[++k], b[++k]};
            c[i] = getChar(cb);
        }
        return new String(c).trim();
    }

    // static helper methods

    private static byte[] getIntBytes(int value) {
        byte[] b = new byte[4];
        b[0] = (byte) ((value >>> 24) & 0xFF);
        b[1] = (byte) ((value >>> 16) & 0xFF);
        b[2] = (byte) ((value >>> 8) & 0xFF);
        b[3] = (byte) ((value >>> 0) & 0xFF);
        return b;
    }

    private static byte[] getLongBytes(long value) {
        byte[] b = new byte[8];
        b[0] = (byte) ((int) (value >>> 56) & 0xFF);
        b[1] = (byte) ((int) (value >>> 48) & 0xFF);
        b[2] = (byte) ((int) (value >>> 40) & 0xFF);
        b[3] = (byte) ((int) (value >>> 32) & 0xFF);
        b[4] = (byte) ((int) (value >>> 24) & 0xFF);
        b[5] = (byte) ((int) (value >>> 16) & 0xFF);
        b[6] = (byte) ((int) (value >>> 8) & 0xFF);
        b[7] = (byte) ((int) (value >>> 0) & 0xFF);
        return b;
    }

    private static byte[] getCharBytes(char value) {
        byte[] b = new byte[2];
        b[0] = (byte) ((value >>> 8) & 0xFF);
        b[1] = (byte) ((value >>> 0) & 0xFF);
        return b;
    }

    private static byte[] getDoubleBytes(double value) {
        byte[] bytes = getLongBytes(Double.doubleToLongBits(value));
        return bytes;
    }

    private static int getInt(byte[] b) {
        assert b.length == 4 : "Invalid number of bytes for integer conversion";
        return ((b[0] << 24) & 0xFF000000) + ((b[1] << 16) & 0x00FF0000) +
                ((b[2] << 8) & 0x0000FF00) + ((b[3] << 0) & 0x000000FF);
    }

    private static long getLong(byte[] b) {
        assert b.length == 8 : "Invalid number of bytes for long conversion";
        int high = getInt(new byte[]{b[0], b[1], b[2], b[3]});
        int low = getInt(new byte[]{b[4], b[5], b[6], b[7]});
        return ((long) (high) << 32) + (low & 0xFFFFFFFFL);
    }

    private static char getChar(byte[] b) {
        assert b.length == 2 : "Invalid number of bytes for char conversion";
        return (char) (((b[0] << 8) & 0x0000FF00) + ((b[1] << 0) & 0x000000FF));
    }

    private static double getDouble(byte[] b) {
        assert b.length == 8 : "Invalid number of bytes for double conversion";
        return Double.longBitsToDouble(getLong(b));
    }


    /* (non-Javadoc)
     * @see org.rrd4j.core.RrdBackend#getHeader()
     */
    @Override
    public Header getHeader() throws IOException {
        return header;
    }

    /* (non-Javadoc)
     * @see org.rrd4j.core.RrdBackend#getDatasource()
     */
    @Override
    public Datasource getDatasource(int index) {
        return datasources[index];
    }

    /* (non-Javadoc)
     * @see org.rrd4j.core.RrdBackend#getArchive()
     */
    @Override
    public Archive getArchive(int index) {
        return archives[index];
    }

    /* (non-Javadoc)
     * @see org.rrd4j.core.RrdBackend#ArcState()
     */
    @Override
    public ArcState getArcState(int arcIndex, int dsIndex) {
        return states[arcIndex][dsIndex];
    }

    @Override
    public Robin getRobin(int arcIndex, int dsIndex) throws IOException {
        return robins[arcIndex][dsIndex];        
    }


    /* (non-Javadoc)
     * @see org.rrd4j.core.RrdBackend#create(org.rrd4j.core.RrdDb, org.rrd4j.core.RrdDef)
     */
    @Override
    public void create(RrdDb rrdDb, RrdDef rrdDef) throws IOException {        
        for(DsDef dsdef: rrdDef.getDsDefs()) {
            String name = dsdef.getDsName();
            if (name != null && name.length() > RrdString.STRING_LENGTH) {
                throw new IllegalArgumentException("Invalid datasource name specified: " + name);
            }
        }
        setLength(getEstimatedSize(rrdDef));

        int version = rrdDef.getVersion();
        int dsCount = rrdDef.getDsCount();
        int arcCount = rrdDef.getArcCount();

        header = new HeaderBinary(allocator, this);
        datasources = new DatasourceBinary[rrdDef.getDsCount()];
        for(int i = 0; i < dsCount; i++) {
            datasources[i] = new DatasourceBinary(allocator, this);
        }
        states = new ArcStateBinary[arcCount][];
        archives = new ArchiveBinary[arcCount];
        robins = new Robin[arcCount][];
        RrdInt[] pointers = version == 2 ? new RrdInt[dsCount] : null;
        for(int i = 0; i < arcCount; i++) {
            int rows = rrdDef.getArcDefs()[i].getRows();
            states[i] = new ArcStateBinary[dsCount];
            archives[i] = new ArchiveBinary(this);
            robins[i] = new Robin[dsCount];
            if (version == 1) {
                for (int j = 0; j < dsCount; j++) {
                    states[i][j] = new ArcStateBinary(allocator, this);
                    robins[i][j] = new RobinArray(this, rows);
                }
            } else {
                for (int j = 0; j < dsCount; j++) {
                    pointers[j] = new RrdInt(archives[i]);
                    states[i][j] = new ArcStateBinary(allocator, this);
                }
                RrdDoubleMatrix values = new RrdDoubleMatrix(archives[i], rows, dsCount);
                for (int j = 0; j < dsCount; j++) {
                    robins[i][j] = new RobinMatrix(this, values, pointers[j], j);
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.rrd4j.core.RrdBackend#load(org.rrd4j.core.RrdDb, org.rrd4j.core.RrdDef)
     */
    @Override
    public void load(RrdDb rrdDb) throws IOException {
        header = new HeaderBinary(allocator, this);
        header.load();

        int dsCount = header.dsCount;
        int arcCount = header.arcCount;

        datasources = new DatasourceBinary[dsCount];
        for(int i = 0; i < dsCount; i++) {
            datasources[i] = new DatasourceBinary(allocator, this);
            datasources[i].load();
        }
        states = new ArcStateBinary[arcCount][];
        archives = new ArchiveBinary[arcCount];
        robins = new Robin[arcCount][];
        RrdInt[] pointers = header.version == 2 ? new RrdInt[dsCount] : null;
        for(int i = 0; i < arcCount; i++) {
            states[i] = new ArcStateBinary[dsCount];
            archives[i] = new ArchiveBinary(this);
            archives[i].load();
            int rows = archives[i].rows;
            robins[i] = new Robin[dsCount];
            if (header.version == 1) {
                for (int j = 0; j < dsCount; j++) {
                    states[i][j] = new ArcStateBinary(allocator, this);
                    states[i][j].load();
                    robins[i][j] = new RobinArray(this, rows);
                    robins[i][j].load();
                }
            } else {
                for (int j = 0; j < dsCount; j++) {
                    pointers[j] = new RrdInt(archives[i]);
                    states[i][j] = new ArcStateBinary(allocator, this);
                    states[i][j].load();
                }
                RrdDoubleMatrix values = new RrdDoubleMatrix(archives[i], rows, dsCount);
                for (int j = 0; j < dsCount; j++) {
                    robins[i][j] = new RobinMatrix(this, values, pointers[j], j);
                    robins[i][j].load();
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.rrd4j.core.RrdBackend#load(org.rrd4j.core.RrdDb, org.rrd4j.core.DataImporter)
     */
    @Override
    public void load(RrdDb rrdDb, DataImporter reader) throws IOException {
        int version = reader.getVersion();
        int dsCount = reader.getDsCount();
        int arcCount = reader.getArcCount();
        int rowsCount = 0;
        for (int i = 0; i < reader.getArcCount() ; i++) {
            rowsCount += reader.getRows(i);
        }
        setLength(calculateSize(dsCount, arcCount, rowsCount, reader.getVersion()));
        
        header = new HeaderBinary(allocator, this);
        datasources = new DatasourceBinary[reader.getDsCount()];
        for(int i = 0; i < dsCount; i++) {
            datasources[i] = new DatasourceBinary(allocator, this);
        }
        states = new ArcStateBinary[arcCount][];
        archives = new ArchiveBinary[arcCount];
        robins = new Robin[arcCount][];
        RrdInt[] pointers = version !=1  ? new RrdInt[dsCount] : null;
        for(int i = 0; i < arcCount; i++) {
            states[i] = new ArcStateBinary[dsCount];
            archives[i] = new ArchiveBinary(this);
            robins[i] = new Robin[dsCount];
            if (version == 1) {
                for (int j = 0; j < dsCount; j++) {
                    states[i][j] = new ArcStateBinary(allocator, this);
                    robins[i][j] = new RobinArray(this, reader.getRows(i));
                    robins[i][j].setValues(reader.getValues(i, j));
                }
            } else {
                for (int j = 0; j < dsCount; j++) {
                    pointers[j] = new RrdInt(archives[i]);
                    states[i][j] = new ArcStateBinary(allocator, this);
                }
                RrdDoubleMatrix values = new RrdDoubleMatrix(archives[i], reader.getRows(i), reader.getDsCount());
                for (int j = 0; j < dsCount; j++) {
                    robins[i][j] = new RobinMatrix(this, values, pointers[j], j);
                    robins[i][j].setValues(reader.getValues(i, j));
                }
            }
        }
    }

    /**
     * Returns the number of storage bytes required to create RRD from this
     * RrdDef object.
     *
     * @return Estimated byte count of the underlying RRD storage.
     */
    public long getEstimatedSize(RrdDef rrdDef) {
        int dsCount = rrdDef.getDsCount();
        int arcCount = rrdDef.getArcCount();
        int rowsCount = 0;
        for (ArcDef arcDef : rrdDef.getArcDefs()) {
            rowsCount += arcDef.getRows();
        }
        int version = rrdDef.getVersion();
        return calculateSize(dsCount, arcCount, rowsCount, version);
    }

    public static final long calculateSize(int dsCount, int arcCount, int rowsCount, int version) {
        return (24L + 48L * dsCount + 16L * arcCount +
                20L * dsCount * arcCount + 8L * dsCount * rowsCount) +
                (1L + 2L * dsCount + arcCount) * 2L * RrdPrimitive.STRING_LENGTH +
                (version == 2 ? 8L * dsCount * arcCount : 0);

    }

    /* (non-Javadoc)
     * @see org.rrd4j.backend.spi.binary.Allocated#getRrdAllocator()
     */
    @Override
    public RrdAllocator getRrdAllocator() {
        return allocator;
    }

    /* (non-Javadoc)
     * @see org.rrd4j.backend.spi.binary.Allocated#getRrdBackend()
     */
    @Override
    public RrdBinaryBackend getRrdBackend() {
        return this;
    }

}

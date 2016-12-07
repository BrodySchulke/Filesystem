import java.util.*;

public class SysLib {
    public static int exec( String args[] ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.EXEC, 0, args );
    }

    public static int join( ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.WAIT, 0, null );
    }

    public static int boot( ) {
	return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.BOOT, 0, null );
    }

    public static int exit( ) {
	return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.EXIT, 0, null );
    }

    public static int sleep( int milliseconds ) {
	return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.SLEEP, milliseconds, null );
    }

    public static int disk( ) {
	return Kernel.interrupt( Kernel.INTERRUPT_DISK,
				 0, 0, null );
    }

    public static int cin( StringBuffer s ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.READ, 0, s );
    }

    public static int cout( String s ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.WRITE, 1, s );
    }

    public static int cerr( String s ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.WRITE, 2, s );
    }

    public static int rawread( int blkNumber, byte[] b ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.RAWREAD, blkNumber, b );
    }

    public static int rawwrite( int blkNumber, byte[] b ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.RAWWRITE, blkNumber, b );
    }

    public static int sync( ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.SYNC, 0, null );
    }

    public static int cread( int blkNumber, byte[] b ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.CREAD, blkNumber, b );
    }

    public static int cwrite( int blkNumber, byte[] b ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.CWRITE, blkNumber, b );
    }

    public static int flush( ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.CFLUSH, 0, null );
    }

    public static int csync( ) {
        return Kernel.interrupt( Kernel.INTERRUPT_SOFTWARE,
				 Kernel.CSYNC, 0, null );
    }

    public static String[] stringToArgs( String s ) {
	StringTokenizer token = new StringTokenizer( s," " );
	String[] progArgs = new String[ token.countTokens( ) ];
	for ( int i = 0; token.hasMoreTokens( ); i++ ) {
	    progArgs[i] = token.nextToken( );
	}
	return progArgs;
    }

    public static void short2bytes( short s, byte[] b, int offset ) {
	b[offset] = (byte)( s >> 8 );
	b[offset + 1] = (byte)s;
    }

    public static short bytes2short( byte[] b, int offset ) {
	short s = 0;
        s += b[offset] & 0xff;
	s <<= 8;
        s += b[offset + 1] & 0xff;
	return s;
    }

    public static void int2bytes( int i, byte[] b, int offset ) {
	b[offset] = (byte)( i >> 24 );
	b[offset + 1] = (byte)( i >> 16 );
	b[offset + 2] = (byte)( i >> 8 );
	b[offset + 3] = (byte)i;
    }

    public static int bytes2int( byte[] b, int offset ) {
	int n = ((b[offset] & 0xff) << 24) + ((b[offset+1] & 0xff) << 16) +
	        ((b[offset+2] & 0xff) << 8) + (b[offset+3] & 0xff);
	return n;
    }

    //Number 1 is intterupt software, 18 is format, numOfFiles is the number of files for formatting
    public static int format(int numOfFiles) {
        return Kernel.interrupt(1, 18, numOfFiles, null);
    }

    //Number 1 is interrupt software, 14 is open, args is the args used for specifying the open type, and the
    //filename is the name of the file
    public static int open(String filename, String mode) {
        String[] args = new String[]{filename, mode};
        return Kernel.interrupt(1, 14, 0, args);
    }

    //Number 1 is interrupt software, 15 is close, ftEntIndex is the index of the file entry to be closed
    public static int close(int ftEntIndex) {
        return Kernel.interrupt(1, 15, ftEntIndex, null);
    }

    //Number 1 is interrupt software, 8 is read, ftEntIndex is the index of the file entry index to read,
    //readInto is the byte array to be read into
    public static int read(int ftEntIndex, byte[] readInto) {
        return Kernel.interrupt(1, 8, ftEntIndex, readInto);
    }

    //Number 1 is interrupt software, 9 is write, ftEntIndex is the index of the file entry to write to,
    //writeFrom is the byte array to write from
    public static int write(int ftEntIndex, byte[] writeFrom) {
        return Kernel.interrupt(1, 9, ftEntIndex, writeFrom);
    }

    //Number 1 is interrupt software, 17 is seek, ftEntIndex is the index of the file entry to seek, offset is the
    //place to start, whence indicates the algorithm for setting the seekptr
    public static int seek(int ftEntIndex, int offset, int whence) {
        int[] data = new int[]{offset, whence};
        return Kernel.interrupt(1, 17, ftEntIndex, data);
    }

    //Number 1 is interrupt software,16 is size, ftEntIndex is the index of the file entry whose size is to
    //be returned
    public static int fsize(int ftEntIndex) {
        return Kernel.interrupt(1, 16, ftEntIndex, null);
    }

    //Number 1 is interrupt software, 19 is delete, the filename is the name of the file to be deleted
    public static int delete(String filename) {
        return Kernel.interrupt(1, 19, 0, filename);
    }
}


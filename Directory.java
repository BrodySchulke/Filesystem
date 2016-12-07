public class Directory {
    private static int maxChars = 30; // max characters of each file name

    // Directory entries
    private int fsizes[];        // each element stores a different file size.
    private char fnames[][];    // each element stores a different file name.
    private static final int FILE_NAME_MAX_NUMBER_OF_BYTES = 60;
    private final static int BYTES_IN_AN_INTEGER = 4;

    public Directory( int maxInumber ) { // directory constructor
        fsizes = new int[maxInumber];     // maxInumber = max files
        for ( int i = 0; i < maxInumber; i++ )
            fsizes[i] = 0;                 // all file size initialized to 0
        fnames = new char[maxInumber][maxChars];
        String root = "/";                // entry(inode) 0 is "/"
        fsizes[0] = root.length( );        // fsize[0] is the size of "/".
        root.getChars( 0, fsizes[0], fnames[0], 0 ); // fnames[0] includes "/"
    }

    public int bytes2directory( byte data[] ) {
        // assumes data[] received directory information from disk
        // initializes the Directory instance with this data[]

        //Go through fsizes and set the actual sizes of all of the files from the byte array data
        int offset = 0;                             //Initialize the offset to start at the beginning
        for (int i = 0; i < fsizes.length; i ++) {
            fsizes[i] = SysLib.bytes2int(data, offset); //Rearrange the 4 bytes into an integer starting at index 0
            offset += BYTES_IN_AN_INTEGER;              //Increment the offset by the number of byets in an integer
        }

        //Loop through fnames setting the name of all of the files from the byte array data
        for (int i = 0; i < fnames.length; i++) {
            String fileName = new String (data, offset, (FILE_NAME_MAX_NUMBER_OF_BYTES)); //Get the name of the file from the byte array
            fileName.getChars(0, fsizes[i], fnames[i], 0);              //Store the file name into the char array fnames
            offset += (FILE_NAME_MAX_NUMBER_OF_BYTES);   //Increment the offset by 60 because each character is 2 bytes
        }
        return 1; //WHY DO WE RETURN 1 HERE I DO NOT ACTUALLY KNOW PLEASE HELP ME
    }

    public byte[] directory2bytes( ) {
        // converts and return Directory information into a plain byte array
        // this byte array will be written back to disk
        // note: only meaningfull directory information should be converted
        // into bytes.

        //Initialize a byte array to convert the meaningful file data frmo the directory into
        //The size is determined by the total bytes in fsizes plus the total byte in fnames to accomodate for them amount of meaningful information
        byte[] fromDirectory = new byte[(BYTES_IN_AN_INTEGER * fsizes.length) + (FILE_NAME_MAX_NUMBER_OF_BYTES * fnames.length)];

        int offset = 0; //Initialize the offset to start at the beginning
        //Loops through fsizes converting all of the integers into bytes and storing them in the new byte array
        for (int i = 0; i < fsizes.length; i++) {
            SysLib.int2bytes(fsizes[i], fromDirectory, offset);    //Convert the int in fsizes into bytes and store them in fromDirectory[offSet] to fromDirectory[offset+3]
            offset += BYTES_IN_AN_INTEGER;
        }

        //Loops through fnames converting file names into byte arrays and storing them in the created byte array for returning
        for (int i = 0; i < fnames.length; i++) {
            String fileName = new String(fnames[i], 0, fsizes[i]); //Get the name of the file from fnames
            byte[] tempFileName = fileName.getBytes();             //Store the bytes of the above filename in a temporary arryay, since getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin) is deprecated and doesn't work properly
            System.arraycopy(tempFileName, 0, fromDirectory, offset, tempFileName.length); //Copy the file name converted into bytes into the created byte array from position offset to the length of the temporary character byte array
            offset += (FILE_NAME_MAX_NUMBER_OF_BYTES);                                     //Increment offset by the amount of bytes in a file name
        }

        return fromDirectory;       //Return the newly created byte array of directory information
    }

    //Looks for a index where the file size is 0 and allocates appropriate file information there for the iNode number
    //that will be allocated for the file with the given name, and returns the index
    public short ialloc( String filename ) {
        // filename is the one of a file to be created.
        // allocates a new inode number for this filename
        short index = findFreeIndex();  //get the first free index, one that contains a size of 0 in fsizes
        if (index == (short)-1)         //if -1 was returned, immediately return -1
        {
            return index;
        }
        //The number of characters in the given file is either the actual amount of characters in the name, or the maxChars if the filename length is too large
        fsizes[index] = (filename.length() < maxChars) ? filename.length() : maxChars;
        filename.getChars(0, fsizes[index], fnames[index], 0);  //Copy the file name up to the number of characters specified above into the same index of fnames, starting from 0
        return index;                                           //Return the iNode number which is the index used to allocate the file details
    }

    //Helper method for ialloc
    //Finds the first free index : where fsizes is 0, and returns the index
    private short findFreeIndex() {
        for (int i = 1; i < fsizes.length; i++) {  //Don't check index at 0, as that is the directory's information and shouldn't be overwritten
            if (fsizes[i] == 0) {                   //Return the value of the current index if fsizes is equal to 0
                return (short)i;
            }
        }
        return (short)-1;                           //Return -1 if no free index of fsizes is found
    }

    //Sets the fsize at the given index to 0 if it is not already 0 and the given index is valid
    //Returns true on success, false otherwise
    public boolean ifree( short iNumber ) {
        // deallocates this inumber (inode number)
        // the corresponding file will be deleted.
        if (maxChars > iNumber && fsizes[iNumber] > 0){
            fsizes[iNumber] = 0;    //Deallocate the file if  iNumber is less than maxChars and the size at index iNumber is 0
            return true;
        }
        return false;
    }

    //Loops through the files looking for the index of the file that has the same exact name as the given name
    //and returns the index of that file if found, otherwise the short value of -1 is returned
    public short namei( String filename ) {
        // returns the inumber corresponding to this filename
        for (int i = 0; i < fsizes.length; i++) {           //Loop through all of fsizes checking to see if the size of a file is equal to the length of the name of the given file
            if (fsizes[i] == filename.length()) {
                String nameOfFile = new String(fnames[i], 0, fsizes[i]);    //Get the name of the found file with the same length by copying it from fnames at the found index
                if (filename.compareTo(nameOfFile) == 0) {                  //If the found name and given name are both the same, return the short value of the index it was found at
                    return (short)i;
                }
            }
        }
        return (short)-1;   //Return short value -1 if no file with given name is found
    }
}
public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    private static final int SEEK_SET = 0;
    private static final int SEEK_CUR = 1;
    private static final int SEEK_END = 2;

    public FileSystem(int diskBlocks) {
        //create superblock, and format disk with 64 inodes default
        superblock = new SuperBlock(diskBlocks);
        //create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.inodeBlocks);
        //file table is created, and store directory in the file table
        filetable = new FileTable(directory);
        //directory reconstruction
        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    public void sync() {
        FileTableEntry syncEntry = open("/", "w");//open the directory for writing
        byte[] data = directory.directory2bytes(); //initialize a byte array with the data from directory
        write(syncEntry, data); //write the data in the entry to disk
        close(syncEntry); //close the file because we're done with it
        superblock.sync();//Syncs the contents of the superblock to disk by converting all necessary data to byte
        //format within a temporary byte array and then writing the contents of the array to disk
    }

    public boolean format(int files) {
        //check that we can actually reformat the file table now
        if (filetable.fempty()) {
            superblock.format(files); //Format the superblock with the given number of files
            directory = new Directory(superblock.inodeBlocks); //Resets the directory by cleaning all aspects of the
            // directory and instantiating only up to iNodeBlocks number of inodeblocks
            filetable = new FileTable(directory); // initialize a new file table given the
            //file systems root directory that was just reformatted
            return true; //signify proper completion
        }
        return false; // cannot format
    }

    public FileTableEntry open(String filename, String mode) {
        //allocate a new entry in the file table with file allocation
        //with the specified file name and mode of access
        FileTableEntry allocateNewEntry = filetable.falloc(filename, mode);
        if (mode.equals("w")) {//if it was a write mode
            if (!deallocAllBlocks(allocateNewEntry)) {//cant deallocate the file if it's in use
                return null;
            } else {
                //it wasn't a write so we can just return the entry as an open file to the caller
                return allocateNewEntry;
            }
        }
        //default return value is to return the allocated entry as long as it wasn't a write
        return allocateNewEntry;
    }

    public boolean close(FileTableEntry ftEnt) {
        //minus a count from the the filetable entry because if there are multiple threads
        //for example, 10 threads using the file table entry and 1 calls close, this will simply decrement
        //to 9 and return true. if there was only 1 thread operating on this file and this file gets closed
        //then the filetable frees the entry from the cache
        return (--ftEnt.count > 0 || filetable.ffree(ftEnt));
    }

    public int fsize(FileTableEntry ftEnt) {
        return ftEnt.inode.length; //return the files size from the i node in bytes
    }

    public int read(FileTableEntry entry, byte[] data) {
        //return error if the mode is write or append
        if (entry.mode.equals("w") && entry.mode.equals("a")) {
            return -1;
        } else { // otherwise, call a recursive read from disk
            return recursiveReadHelper(data.length, entry, data, 0);//return the read result found in recursive wind
        }
    }

    private int recursiveReadHelper(int dataLength, FileTableEntry entry, byte[] data, int readReturn) {
        int targetBlockPtr; //scoped variable to be set for the target block to extract
        if (dataLength > 0 && entry.seekPtr < fsize(entry)) {//if there's stuff to read
            targetBlockPtr = entry.inode.findTargetBlock(entry.seekPtr);//start the target block ptr
            if (targetBlockPtr == -1) {//base case complete read
                return readReturn;
            }
            //initialize the from disk
            byte[] fromDisk = new byte[512];
            //read the block into from disk
            SysLib.rawread(targetBlockPtr, fromDisk);
            int blockLoc = entry.seekPtr % 512; //this will return what block you're in
            //return how much data actually needs to be read, because that will show how far
            //the seeker has gotten on the file, based on the size of the file remaining positive
            int min = Math.min(Math.min(512 - blockLoc, dataLength), fsize(entry) - entry.seekPtr);
            //copying data from the disk at the given block location into data, and the start position for the dest
            //given the length defined by minimum which is grabbed from extracting the correct block index
            //on the disk
            System.arraycopy(fromDisk, blockLoc, data, readReturn, min);
            //add to the seek ptr to increment the read location
            entry.seekPtr += min;
            //the readReturn gets as well because it's where the seekPtr ends up
            //you need to read min less spaces now
            //keep winding the stack and return where the seek ptr ends up
            return recursiveReadHelper(dataLength - min, entry, data, readReturn + min);
        } else { //base case, complete read
            return readReturn;
        }
    }

    public int write(FileTableEntry entry, byte[] data) {
        if(entry.mode.equals("r")) {//if it was a read, then you're calling the wrong method
            return -1;
        } else {//do the write work
            return recursiveWriteHelper(data.length, 0, entry, data);
        }
    }

    private int recursiveWriteHelper(int length, int writeReturn, FileTableEntry entry, byte[] data) {
        int targetBlock;//set up a scoped variable to access the target block
        if (length > 0) {//recursive check
             targetBlock = entry.inode.findTargetBlock(entry.seekPtr);//set the target block to whatever block the
            //seek ptr is located in
            if (targetBlock == -1) {//if the block wasn't found
                short freeBlock = (short)superblock.getFreeBlock();//get the first free block
                int registerReturn = entry.inode.registerTargetBlock(entry.seekPtr, freeBlock);//register the target block
                //using the seek ptr from the entry and the closest free block
                if (registerReturn == -1) {//if the register was bad
                    SysLib.cerr("ThreadOS: filesystem panic on write\n");
                    return -1;
                }
                else if (registerReturn == -3) {//if the register could've been possibly bad
                    short nextFreeBlock = (short)superblock.getFreeBlock();//get the next free block again
                    //means it tried to access an indirect block that hadn't been set properly
                    //so it tried again
                    //if it didn't work again, notify a panic state
                    if (!entry.inode.registerIndexBlock(nextFreeBlock) || entry.inode.registerTargetBlock(entry.seekPtr, freeBlock) != 0) {
                        SysLib.cerr("ThreadOS: panic on write\n");
                        return -1;
                    } else {//otherwise, the second time the register was good
                        targetBlock = freeBlock;
                    }
                } else {//the register was good
                    targetBlock = freeBlock;
                }

            }
            //get the value to step the pointer forward
            int min = minAndWriteHelper(targetBlock, new byte[512], length, data, writeReturn, entry);
            //wind the stack and return where the end of file ends up
            return recursiveWriteHelper(length - min, writeReturn + min, entry, data);
        } else {//base case
            //write the entry to disk, and return where the seek ptr ended
            entry.inode.toDisk(entry.iNumber);
            return writeReturn;
        }
    }
    //Method that helps the recursive helper by copying data from the data array into a temporary storgae and then
    //writing that to the disk, while being sure to increment the seekptr and return the resulting min value
    private int minAndWriteHelper(int targetBlock, byte[] toDisk, int length, byte[] data, int writeReturn, FileTableEntry entry) {
        if (SysLib.rawread(targetBlock, toDisk) == -1) { //Call a system exit if the rawread fails
            System.exit(2);
        }
        int blockLoc = entry.seekPtr % 512; //Get the block number that the pointer is in
        int min = Math.min(512 - blockLoc, length); //See if the pointer location is less than the length, assign whatever is less
        System.arraycopy(data, writeReturn, toDisk, blockLoc, min); //Copy the data from the data array to the byte array for temporary storage
        SysLib.rawwrite(targetBlock, toDisk); //Write the temporary storage data to disk at the target block
        entry.seekPtr += min; //Increment the seek pointer
        if (entry.seekPtr > entry.inode.length) { //Assign the inode length to the seekptr if seekptr is greater
            entry.inode.length = entry.seekPtr;
        }
        return min; //return the resulting min value
    }

    private boolean deallocAllBlocks(FileTableEntry entry) {
        if(entry.inode.count != 1) { //Don't deallocate if more entries are pointing to this
            return false; //And return false
        } else {
            //Otherwise unregister the index block and store it in a byte array
            byte[] unregisteredIndexBlock = entry.inode.unregisterIndexBlock();
            //If the byte array isn't null
            if(unregisteredIndexBlock != null) {
                short blockLoc;
                //While there is more  deallocations to occur within the blocks
                while((blockLoc = SysLib.bytes2short(unregisteredIndexBlock, 0)) != -1) {
                    //deallocate the blocks
                    superblock.returnBlock(blockLoc);
                }
            }
            //Return the result of the recursive helper (true)
            return deallocRecursiveHelper(0, entry);
        }
    }

    private boolean deallocRecursiveHelper(int directAccess, FileTableEntry entry){
        if (directAccess >= 11) { //If we have finished all direct access
            //Write to disk and return true
            entry.inode.toDisk(entry.iNumber);
            return true;
        }
        //If the directAccess index is not already deallocated
        if(entry.inode.direct[directAccess] != -1) {
            //Deallocate the block
            superblock.returnBlock(entry.inode.direct[directAccess]);
            //Set the index to -1
            entry.inode.direct[directAccess] = -1;
        }
        //Recursive call with incrementing directAccess
        return deallocRecursiveHelper(++directAccess, entry);
    }

    public boolean delete(String filename) {
        //Open the file to be deleted
        FileTableEntry deleteEntry = open(filename, "w");
        //Get the inumber of the entry to be deleted
        short iNum = deleteEntry.iNumber;
        //Return true if the entry can be closed and completely freed
        return (close(deleteEntry) && directory.ifree(iNum));
    }

    public int seek(FileTableEntry entry, int offset, int whence) {
        if (whence == SEEK_SET) { //If whence is 0
            //Check to see if the offset is less than 0, and set the seekptr to 0 if so
            if (offset < 0) {
                entry.seekPtr = 0;
                return entry.seekPtr; //return the seekptr
                //If the offset is greater than the file size, set the seekptr to filesize
            } else if (offset > fsize(entry)) {
                entry.seekPtr = fsize(entry);
                return entry.seekPtr; //return seekptr
            } else {
                entry.seekPtr = offset; //Otherwise set the seekptr to offset and return
                return entry.seekPtr;
            }
        } else if (whence == SEEK_CUR) { //If whence is 1
            //If the value to be set is less than 0, set the seekptr to 0
            if (offset + entry.seekPtr < 0) {
                entry.seekPtr = 0;
                return entry.seekPtr; //return the seekptr
                //If the value to be set is greater than file size, set the seekptr to file size
            } else if (offset + entry.seekPtr > fsize(entry)) {
                entry.seekPtr = fsize(entry);
                return entry.seekPtr; //return the seektpr
            } else {
                entry.seekPtr += offset; //Otherwise add offset to the seekptr and return
                return entry.seekPtr;
            }
        } else if (whence == SEEK_END) { //If whhence is 2
            //If the value to be set is less than 0, set the seekptr to 0
            if (offset + fsize(entry) < 0) {
                entry.seekPtr = 0;
                return entry.seekPtr; //return the seekptr
                //If the value to be set is greater than file size, set the seekptr to file size
            } else if (offset + fsize(entry) > fsize(entry)) {
                entry.seekPtr = fsize(entry);
                return entry.seekPtr; //Return the seekptr
            } else {
                entry.seekPtr = offset + fsize(entry); //Set the seekptr to offset + file size
                return entry.seekPtr; //return the seekpointer
            }
        } else {
            return entry.seekPtr; //return the seek pointer
        }
    }
}
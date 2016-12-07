public class Inode {
    private final static int iNodeSize = 32;       // fix to 32 bytes
    private final static int directSize = 11;      // # direct pointers
    private final static int MAX_INODES = 16;
    private final static int MAX_BLOCK_SIZE_BYTES = 512;
    private final static int BYTES_IN_AN_INTEGER = 4;
    private final static int BYTES_IN_A_SHORT = 2;

    public int length;                             // file size in bytes
    public short count;                            // # file-table entries pointing to this
    public short flag;                             // 0 = unused, 1 = used, ...
    public short direct[] = new short[directSize]; // direct pointers
    public short indirect;                         // a indirect pointer

    public Inode( ) {                                     // a default constructor
        length = 0;
        count = 0;
        flag = 1;
        for (int i = 0; i < directSize; i++) { direct[i] = -1; }
        indirect = -1;
    }

    //Method which will initialize an Inode object from the disk by reading from various blocks into a byte array
    //and setting specific values initially determined by the given iNumber as an argument
    public Inode( short iNumber ) {                            // retrieving inode from disk
        int block = (1 + (iNumber / MAX_INODES));       //Determine which block number the given iNumber is in
        byte[] data = new byte[MAX_BLOCK_SIZE_BYTES];              //Each block on disk contains 512 bytes
        SysLib.rawread(block, data);                    //Read the data from the given block into the byte array
        int offset = (iNumber % MAX_INODES) * iNodeSize; //Initialize the offset as the start of where to operate
        length = SysLib.bytes2int(data, offset);  //rearrange the 4 bytes into a single integer by adding them together
        offset += BYTES_IN_AN_INTEGER;            //add to the offset the number of bytes in an integer to move forward
        count = SysLib.bytes2short(data, offset); //rearrange the 2 bytes into a single short from the offset
        offset += BYTES_IN_A_SHORT;               //add to the offset the number of bytes in a short to move forward
        flag = SysLib.bytes2short(data, offset);  //set the flag by rearranging the bytes into a short
        offset += BYTES_IN_A_SHORT;               //add to the offset and move forward by a short
        //we have already moved 8 bytes, time to move forward
        //for the remaining 22 bytes to get use to 30 bytes and fill up the actual material for the first 11
        //which are direct blocks
        for (int i = 0; i < directSize; i++) {
            direct[i] = SysLib.bytes2short(data, offset);
            //move the offset forward 2 bytes at a time
            offset += BYTES_IN_A_SHORT;
        }
        //now move to the indirect block to point the offset to the end
        indirect = SysLib.bytes2short(data, offset);
        //increment the offset to point at the indirect block
        offset += BYTES_IN_A_SHORT;
    }

    public int toDisk( short iNumber ) {            // save to disk as the i-th inode
        byte[] data = new byte[iNodeSize];         //initialize the byte array to read one whole iNode
        int offset = 0;                            //start the offset to operate from
        SysLib.int2bytes(length, data, offset);     //convert length back into a byte and store it in data
        offset += BYTES_IN_AN_INTEGER;              //add to the offset the number of bytes in an integer
        SysLib.short2bytes(count, data, offset);    //convert count back into a byte and store it in data
        offset += BYTES_IN_A_SHORT;                 //add to the offset the number of bytes in a short
        SysLib.short2bytes(flag, data, offset);     //convert flag back into a byte and store it in data
        offset += BYTES_IN_A_SHORT;                 //add to the offset the number of bytes in a short
        //Loop through the direct blocks converting them to bytes and storing them in data and incrementing offset
        //by the next 22 bytes to get to 30 byes
        for (int i = 0; i < directSize; i++) {
            SysLib.short2bytes(direct[i], data, offset);
            offset += BYTES_IN_A_SHORT;
        }
        SysLib.short2bytes(indirect, data, offset); //convert indirect block into bytes and store it in data
        offset += BYTES_IN_A_SHORT;                 //add to the offset the number of bytes in a short, now at 32 bytes
        //The following ensures there is no inconsistency among different user threads of any iNode
        //The iNode is read from the disk, and then write its contents back immediately
        int block = (1 + (iNumber / MAX_INODES));       //Determine which block number the given iNumber is in
        //inumber / maxinodes(16) to get to the block index
        byte[] actualData = new byte[MAX_BLOCK_SIZE_BYTES]; //Each block on disk contains 512 bytes, this will contain
        //the actual data
        SysLib.rawread(block, actualData);              //Now read the iNode from disk into the byte array to prevent
        //writing to an iNode while another thread is reading from it
        offset = (iNumber % MAX_INODES) * iNodeSize; //Initialize the offset as the start of where to operate
        System.arraycopy(data, 0, actualData, offset, iNodeSize); //copy the information from initial data into the
        //actual data byte array
        SysLib.rawwrite(block, actualData);         //write the iNode information to disk now that it has been updated
        return 0;
    }

    //finds the index of the desired block from the given seekPtr offset position
    public int findTargetBlock(int seekPtrOffset) {
        //finding the index to represent the block it's part of
        int targ = seekPtrOffset / MAX_BLOCK_SIZE_BYTES;
        //if it's less than the size, there is direct access
        //so simply return the pointer to the direct access at that target index
        if (targ < directSize) {
            return direct[targ];
            //indirect points to an indirect block
            //this allows for indirection to split the block into pieces
        } else if (indirect < 0) {//if the indirect is invalid
            return -1;//return invalid block num
            //otherwise we know it's an indirect block
        } else {
            //make a new byte array to copy the data from the indirect block
            byte[] targetBlockData = new byte[MAX_BLOCK_SIZE_BYTES];
            //read the data from the indirect into the byte array
            SysLib.rawread(indirect, targetBlockData);
            //get the offset from the target by subtracting directSize (the 11 amount specified)
            int offset = (targ - directSize);//guaranteed not to be negative
            offset *= 2; //multiply offset by two to put the bytes2short into the targetblockdata
            return SysLib.bytes2short(targetBlockData, offset);//make syslib read it in to the array
        }
    }

    public boolean registerIndexBlock(short freeBlock) {
        if (indirect != -1) {//otherwise if the indirect has already been registered
            return false;//then we say we can't register the new one
        }
        //iterate through all the direct access array
        for (int i = 0; i < directSize; i++) {
            //if anything in direct access was -1, it hasn't been set
            if (direct[i] == -1) {//so simply
                return false;//return false to indicate that not all of the direct blocks have been set
                //so there's no point in setting the indirect index block at this point
            }
        }
        //else, then we know we can set the indirect block
        indirect = freeBlock;//so allocate the indirect index to point to the free block
        byte[] data = new byte[MAX_BLOCK_SIZE_BYTES];//create the array for the size of the block
        for (int j = 0; j < MAX_BLOCK_SIZE_BYTES / 2; j++) {//then iterate through for half the size because short
            int offset = j * 2;//and set the offset to be read into the byte array
            SysLib.short2bytes((short)-1, data, offset);//actually read it in
        }
        SysLib.rawwrite(freeBlock, data);//then write it to the data block from the freeblock
        return true;//and return true to indicate proper indexes

    }

    public int registerTargetBlock(int seekPtr, short freeBlock) {
        //this method registers a target block based on the seekPtr and the freeblock short
        int regTarg = seekPtr / MAX_BLOCK_SIZE_BYTES;//the register target is the seekptr divided by blocksize
        //if the block is within the direct size indexing
        if(regTarg < directSize) {
            //if the register target block is in a good index range and the direct array
            //at the block minus one is not set
            if (regTarg > 0 && direct[regTarg - 1] == -1) {
                return -2;//signal for regtarg at this val
            } else if (direct[regTarg] >= 0) {//otherwiser, if the direct at the registar target was set
                return -1;//then indicate that we shouldn't register
            } else {//otherwise, set the direct array at the regtarg index to be the freeblock point
                direct[regTarg] = freeBlock;
                return 0;//return the 0 to signal for regTarg
            }
        } else if(indirect < 0) {//if the indirect wasn't set
            return -3;//this isn't relevant to this method because this is about regging direct blocks
        } else {//otherwise, reg targ is equivalent to the indirect value
            byte[] targBlkData = new byte[MAX_BLOCK_SIZE_BYTES];//create the byte array for targ blk data
            SysLib.rawread(indirect, targBlkData);//read from the indirect
            //set the offset as the registered target - the direct array access
            int offset = (regTarg - directSize) * 2;
            if (SysLib.bytes2short(targBlkData, offset) <= 0) {//see if the resulting
                //short from bytes2short in targetblkdata at the offset is less than equal to 0
                //if it was
                SysLib.short2bytes(freeBlock, targBlkData, offset);//then the bytes need to be read in to the targ
                //because the bytes need to be read back in to be written back into the disk
                //and then written from indirect and targblkdata
                SysLib.rawwrite(indirect, targBlkData);
                return 0;//return the 0 to signal for regTarg as success
            } else {
                return -1;//return the error as signal for regtarg
            }
        }
    }

    public byte[] unregisterIndexBlock() {
        //if the indirect pointer is actually pointing to something
        if (indirect >= 0) {
            //then we're going to need a byte array to write all the bytes to for the retVal
            byte[] indexBlock = new byte[MAX_BLOCK_SIZE_BYTES];
            //read the bytes from the indirect block index onward to fill up the indexBlock data
            SysLib.rawread(indirect, indexBlock);
            indirect = -1; //we wanted to unregister, so set the indirect pointer to -1 so it's invalidated
            return indexBlock; //return that byte array
        } else {
            //otherwise the indirect wasn't pointing to anything so return null
            return null;
        }
    }
}
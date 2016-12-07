public class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks; //the number of disk blocks
    public int inodeBlocks; //the number of inodes
    public int freeList;   //the block number of the free list's head
    public final static int BYTES_IN_AN_INT = 4;

    //Initializes the data of a superblock using the disksize parameter passed in, which represents the number of blocks on disk
    //Assigns values to totalBlocks, inodeBlocks, and freeList byt comverting the byte data read from the disk into ints and
    //assigning the values to the appropriate vairables, also error checks for disk contents and formats if necessary
    public SuperBlock(int diskSize) {
        int offset = 0; //Initialize an offset for incrementing with bytes to int
        byte[] superBlock = new byte[512];  //superBlock array size is 512 because that is the max disk block size
        SysLib.rawread(offset, superBlock); //Read data from the disk from offset and store it in superBlock
        totalBlocks = SysLib.bytes2int(superBlock, offset); //Convert the bytes in superBlock to an int to represent the total blocks
        offset += BYTES_IN_AN_INT;          //Increment offset by 4 because that is the number of bytes in an int
        inodeBlocks = SysLib.bytes2int(superBlock, offset); //Convert the bytes in superblock from index 4 to an int to represent inodeBlocks
        offset += BYTES_IN_AN_INT;           //Increment offset by 4 because that is the number of bytes in an int
        freeList = SysLib.bytes2int(superBlock, offset);//Convert the bytes in superblock from index 8 to an int to represent freeList
        //Validates the contents of the disk by ensuring that the total blocks match up with the disk size, that iNode blocks is greater than 0
        //and that the block number of the free list's head is greater than 1
        if(totalBlocks != diskSize || inodeBlocks <= 0 || freeList < 2) {
            //If the contents aren't valid, set the total blocks to disk size and format the disk
            totalBlocks = diskSize;
            format(defaultInodeBlocks);
        }
    }
    //Syncs the contents of the superblock to disk by converting all necessary data to byte format within a temporary
    //byte array and then writing the contents of the array to disk
    public void sync() {
        int offset = 0; //Initiallize the offset for incrementing with int to bytes
        byte[] toDisk = new byte[512];  //Temporary byte array to store converted contents for writing to disk
        SysLib.int2bytes(totalBlocks, toDisk, offset);      //Convert the totalblocks data to bytes and store in toDisk at offset 0
        offset += BYTES_IN_AN_INT; //Increment offset by 4 because that is the number of bytes in an int
        SysLib.int2bytes(inodeBlocks, toDisk, offset); //Convert the inodeBlocks data to bytes and store in toDisk at offset 4
        offset += BYTES_IN_AN_INT; //Increment offset by 4 because that is the number of bytes in an int
        SysLib.int2bytes(freeList, toDisk, 8); //Convert the freeList data to bytes and store in toDisk at offset 8
        SysLib.rawwrite(0, toDisk); //Write the converted contents to disk starting at index 0
    }

    //Format cleans the disks and completely formats it to its appropriate form if any invalid contents of the disk
    //are detected by the superblock
    public void format(int numberOfinodeBlocks) {
        inodeBlocks = numberOfinodeBlocks;  //inode blocks is set to the argument passed, which is set to the default of 64

        //Loop through all of the inodes completely resetting them all to their default values as a part
        //of reformatting the disk when invalid contents are detected
        for (int i = 0; i < inodeBlocks; i++) {
            Inode formatInode = new Inode();
            formatInode.flag = 0;
            formatInode.toDisk((short) i);
        }


        this.freeList = 2 + (inodeBlocks / 16); //Freelist is reset and initialized to 2 + numer of inode blocks divided by 16
        //because 16 inodes can be stored in each block

        //From free list, go through the total number of blocks to continue formtating the disk
        for (int i = freeList; i < totalBlocks; i++) {
            byte[] nothing = new byte[512]; //byte array called nothing because they will all contain default values as a part of formatting
            //Innter loop for looping though each byte array after it is created assigning the default values as mentioned above
            for (int j = 0; j < 512; j++) {
                nothing[j] = 0;
            }
            //Convert the outer loop value into byte format starting at offset 0 and store the bytes into the
            //temporary nothing byte array
            SysLib.int2bytes(i + 1, nothing, 0);
            SysLib.rawwrite(i, nothing);    //Write the contents of the nothing array to disk starting at index i, the value of the outer loop
        }
        sync(); //Now sync the contents of the superblock to the disk since the disk was formatted correctly
    }

    //Method finds the first free block from within the free list, where the free list's block is at the top
    public int getFreeBlock() {
        int freeBlock = freeList; //Assign the return value to the value of freeList
        //If the freeList location is valid
        if (freeBlock >= 0) {
            //ensured that the freeblock copied by freeList is not invalid
            byte[] toDisk = new byte[512];//Create a byte array to get the contents of the free block
            SysLib.rawread(freeBlock, toDisk);  //Read the contents from freeList location into the byte array
            freeList = SysLib.bytes2int(toDisk, 0);//Now update the bytes read from disk into an int and assign it to freeList, as it is updated
            SysLib.int2bytes(0, toDisk, 0); //Convert the contents back into bytes to write the updated freeList informtion to the disk
            SysLib.rawwrite(freeBlock, toDisk);//Write the updated information back to disk now
        }
        //this will return invalid if the freelist was invalid as error
        return freeBlock; //Return the value of the free block that was found
    }

    //Method adds a freed block to the free list, returning an error if the block that is free is not valid
    public boolean returnBlock(int blockNum) {
        if(blockNum < 0) {// the blocknumber cannot be in a non indexed location or located backwards
            return false;//so return false
        } else {//otherwise we're good we can add the block
            byte[] addBlock = new byte[512];//create the byte array for the super block to add the free block
            //iterate for the size of the block and set everything to empty array
            for (int i = 0; i < addBlock.length; i++) {
                addBlock[i] = 0;
            }
            //convert the contents of the byte array in an integer
            //just adds a single freed block that's why it only does this once
            SysLib.int2bytes(freeList, addBlock, 0);
            //write that block to disk
            SysLib.rawwrite(blockNum, addBlock);
            //show where the freedblock is with freelist
            freeList = blockNum;
            //return to signify freeing the block to the free list
            return true;
        }
    }
}

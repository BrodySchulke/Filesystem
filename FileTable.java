import java.util.Vector;

public class FileTable {

    private Vector<FileTableEntry> table;         // the actual entity of this file table
    private Directory dir;        // the root directory

    public FileTable(Directory directory ) { // constructor
        table = new Vector<FileTableEntry>();     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system
    //falloc is responsible for allocating a new file table entry based on the given filename and requested mode
    //through initializing a new inode appropriately and setting flags, returning the file table entry resulted if successful
    //or through returning null if unsuccessful
    public synchronized FileTableEntry falloc(String filename, String mode) {
        Inode nodei;//create a scoped inode variable
        short iNum = -1; //Create a scoped inode number index to get to the inode
        if(filename.compareTo("/") != 0) {//if the filename is not the root the root
            iNum = dir.namei(filename); //iNum is equal to the index of the given filename in the entrys of inodes in directory
        } else {
            iNum = 0;   //Otherwise iNum is set to 0
        }
        if (iNum >= 0) { //If the Inode was found with a valid filename
            nodei = new Inode(iNum);    //Initialize a new Inode with the iNum
            nodei.flag = 2;         //Set the new Inode's flag to 2
        }
        else if (mode.equals("r")) { //Otherwise if the Inode wasn't found and it is a read request
            return null;            //Return null because we don't create a file for reading
        }
        else {  //In this case we are trying to write or append
            iNum = dir.ialloc(filename); //Allocate space for a file with the given file name
            nodei = new Inode();         //Initialize an Inode object
            nodei.flag = 2;         //Set the new Inode's flag to 2
        }
        //Increment this inodes count
        nodei.count++;
        //Immediately write back this inode to the disk
        nodei.toDisk(iNum);
        //Initialize a file table entry for returning
        FileTableEntry ftEntry = new FileTableEntry(nodei, iNum, mode);
        //Insert the new file table entry into the file table
        table.addElement(ftEntry);
        //Return the new file table entry
        return ftEntry;
    }


    //ffree is responsible for freeing the file table entry from the cache
    public synchronized boolean ffree(FileTableEntry entry) {
        //if the entry is successfully removed fro the table, decrement the entry's inode count and start doing checks
        if(table.removeElement(entry)) {
            entry.inode.count--;
            //If the inode of the entry's flag is either 1 or 2 (used or read), set it to 0 (unused)
            if (entry.inode.flag == 1 || entry.inode.flag == 2) {
                entry.inode.flag = 0;
            }
            //If the inode of the entry's flag is either 4 or 5 (read/write or append), set it to 3 (write)
            else if(entry.inode.flag == 4 || entry.inode.flag == 5) {
                entry.inode.flag = 3;
            }
            //Write the entry's new inode information to disk at the iNumber index
            entry.inode.toDisk(entry.iNumber);
            //wake up the calling waiting thread and return true
            notify();
            return true;
        } else {
            //return false if the entry is not successfully removed
            return false;
        }
    }
    //Returns true if the table is empty
    public synchronized boolean fempty( ) {
        return table.isEmpty( );  // return if table is empty
    }                            // should be called before starting a format
}
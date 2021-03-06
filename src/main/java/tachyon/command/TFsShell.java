package tachyon.command;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Collections;

import org.apache.thrift.TException;

import tachyon.thrift.FileDoesNotExistException;
import tachyon.thrift.FileAlreadyExistException;
import tachyon.thrift.InvalidPathException;
import tachyon.thrift.ClientFileInfo;

import tachyon.CommonUtils;
import tachyon.client.OpType;
import tachyon.client.OutStream;
import tachyon.client.TachyonClient;
import tachyon.client.TachyonFile;

/**
 * Class for handling command line inputs.
 */ 
public class TFsShell {
  public void printUsage(String cmd) {}
  public void close() {}

  /**
   * Displays information for all directories and files directly under the path specified in argv.
   * @param argv[] Array of arguments given by the user's input from the terminal
   * @return 0 if command is successful, -1 if an error occurred.
   * @throws FileDoesNotExistException
   * @throws InvalidPathException
   * @throws TException
   */
  public int ls(String argv[])
      throws FileDoesNotExistException, InvalidPathException, TException {
    if (argv.length != 2) {
      System.out.println("Usage: tfs ls <path>");
      return -1;
    }
    String path = argv[1];
    String folder = Utils.getFilePath(path);
    TachyonClient tachyonClient = TachyonClient.getClient(Utils.getTachyonMasterAddress(path));
    List<ClientFileInfo> files = tachyonClient.listStatus(folder);
    Collections.sort(files);
    String format = "%-10s%-25s%-15s%-5s\n";
    for (ClientFileInfo file : files) {
      System.out.format(format, CommonUtils.getSizeFromBytes(file.getSizeBytes()),
          CommonUtils.convertMsToDate(file.getCreationTimeMs()), 
          file.isInMemory() ? "In Memory" : "Not In Memory", file.getPath());
    }
    return 0;
  }

  /**
   * Creates a new directory specified by the path in argv, including any parent folders that
   * are required. This method fails if a directory or file with the same path already exists.
   * @param argv[] Array of arguments given by the user's input from the terminal
   * @return 0 if command is successful, -1 if an error occurred.
   * @throws InvalidPathException
   */
  public int mkdir(String argv[]) throws InvalidPathException, FileAlreadyExistException {
    if (argv.length != 2) {
      System.out.println("Usage: tfs mkdir <path>");
      return -1;
    }
    String path = argv[1];
    String folder = Utils.getFilePath(path);
    TachyonClient tachyonClient = TachyonClient.getClient(Utils.getTachyonMasterAddress(path));
    if (tachyonClient.mkdir(folder) != -1) {
      System.out.println("Successfully created directory " + folder);
      return 0;
    } else {
      return -1;
    }
  }

  /**
   * Removes the file or directory specified by argv. Will remove all files and directories in
   * the directory if a directory is specified.
   * @param argv[] Array of arguments given by the user's input from the terminal
   * @return 0 if command is successful, -1 if an error occurred.
   * @throws FileDoesNotExistException
   * @throws InvalidPathException
   * @throws TException
   */
  public int rm(String argv[]) 
      throws FileDoesNotExistException, InvalidPathException, TException {
    if (argv.length != 2) {
      System.out.println("Usage: tfs rm <path>");
      return -1;
    }
    String path = argv[1];
    String file = Utils.getFilePath(path);
    TachyonClient tachyonClient = TachyonClient.getClient(Utils.getTachyonMasterAddress(path));
    if (tachyonClient.delete(file)) {
      System.out.println(file + " has been removed");
      return 0;
    } else {
      return -1;
    }
  }

  /**
   * Renames a file or directory specified by argv. Will fail if the new path name already
   * exists.
   * @param argv[] Array of arguments given by the user's input from the terminal
   * @return 0 if command is successful, -1 if an error occurred.
   * @throws FileDoesNotExistException
   * @throws InvalidPathException
   * @throws TException
   */
  public int rename(String argv[]) 
      throws FileDoesNotExistException, InvalidPathException, TException {
    if (argv.length != 3) {
      System.out.println("Usage: tfs mv <src> <dst>");
      return -1;
    }
    String srcPath = argv[1];
    String dstPath = argv[2];
    InetSocketAddress srcMasterAddr = Utils.getTachyonMasterAddress(srcPath);
    InetSocketAddress dstMasterAddr = Utils.getTachyonMasterAddress(dstPath);
    if (!srcMasterAddr.getHostName().equals(dstMasterAddr.getHostName()) || 
        srcMasterAddr.getPort() != dstMasterAddr.getPort()) {
      throw new InvalidPathException("The file system of source and destination must be the same");
    }
    String srcFile = Utils.getFilePath(srcPath);
    String dstFile = Utils.getFilePath(dstPath);
    TachyonClient tachyonClient = TachyonClient.getClient(srcMasterAddr);
    if (tachyonClient.rename(srcFile, dstFile)) {
      System.out.println("Renamed " + srcFile + " to " + dstFile);
      return 0;
    } else {
      return -1;
    }
  }

  /**
   * Copies a file specified by argv from the filesystem to the local filesystem.
   * @param argv[] Array of arguments given by the user's input from the terminal
   * @return 0 if command is successful, -1 if an error occurred.
   * @throws FileDoesNotExistException
   * @throws InvalidPathException
   * @throws TException
   * @throws IOException
   */
  public int copyToLocal(String argv[])
      throws FileDoesNotExistException, InvalidPathException, TException, IOException {
    if (argv.length != 3) {
      System.out.println("Usage: tfs copyToLocal <src> <localdst>");
      return -1;
    }

    String srcPath = argv[1];
    String dstPath = argv[2];
    String folder = Utils.getFilePath(srcPath);
    File dst = new File(dstPath);
    TachyonClient tachyonClient = TachyonClient.getClient(Utils.getTachyonMasterAddress(srcPath));
    TachyonFile tFile = tachyonClient.getFile(folder);

    // tachyonClient.getFile() catches FileDoesNotExist exceptions and returns null
    if (tFile == null) {
      throw new FileDoesNotExistException(folder);
    }

    ByteBuffer buf = tFile.readByteBuffer();
    FileOutputStream out = new FileOutputStream(dst);
    FileChannel channel = out.getChannel();
    while (buf.hasRemaining()) {
      channel.write(buf);
    }
    channel.close();
    out.close();
    tFile.releaseFileLock();
    System.out.println("Copied " + srcPath + " to " + dstPath);
    return 0;
  }

  /**
   * Displays a list of hosts that have the file specified in argv stored.
   * @param argv[] Array of arguments given by the user's input from the terminal
   * @return 0 if command is successful, -1 if an error occurred.
   * @throws FileDoesNotExistException
   * @throws InvalidPathException
   * @throws IOException
   * @throws TException
   */
  public int location(String argv[]) 
      throws FileDoesNotExistException, InvalidPathException, IOException, TException {
    if (argv.length != 2) {
      System.out.println("Usage: tfs location <path>");
      return -1;
    }
    String path = argv[1];
    String file = Utils.getFilePath(path);
    TachyonClient tachyonClient = TachyonClient.getClient(Utils.getTachyonMasterAddress(path));
    int fileId = tachyonClient.getFileId(file);
    List<String> hosts = tachyonClient.getFileHosts(fileId);
    System.out.println(file + " with file id " + fileId + " are on nodes: ");
    for (String host: hosts) {
      System.out.println(host);
    }
    return 0;
  }

  /**
   * Copies a file specified by argv from the local filesystem to the filesystem. Will fail if
   * the path given already exists in the filesystem.
   * @param argv[] Array of arguments given by the user's input from the terminal
   * @return 0 if command is successful, -1 if an error occurred.
   * @throws FileDoesNotExistException
   * @throws InvalidPathException
   * @throws IOException
   */
  public int copyFromLocal(String argv[]) 
      throws FileNotFoundException, InvalidPathException, IOException, FileAlreadyExistException {
    if (argv.length != 3) {
      System.out.println("Usage: tfs copyFromLocal <src> <remoteDst>");
      return -1;
    }

    String srcPath = argv[1];
    String dstPath = argv[2];
    String dstFile = Utils.getFilePath(dstPath);
    File src = new File(srcPath);
    if (!src.exists()) {
      System.out.println("Local file " + srcPath + " does not exist.");
      return -1;
    }
    TachyonClient tachyonClient = TachyonClient.getClient(Utils.getTachyonMasterAddress(dstPath));
    int fileId = tachyonClient.createFile(dstFile);
    if (fileId == -1) {
      return -1;
    }
    TachyonFile tFile = tachyonClient.getFile(fileId);
    OutStream os = tFile.createOutStream(OpType.WRITE_THROUGH);
    FileInputStream in = new FileInputStream(src);
    FileChannel channel = in.getChannel();
    ByteBuffer buf = ByteBuffer.allocate(1024);
    while (channel.read(buf) != -1) {
      buf.flip();
      os.write(buf);
    }
    os.close();
    channel.close();
    in.close();
    System.out.println("Copied " + srcPath + " to " + dstPath);
    return 0;
  }

  /**
   * Method which prints the method to use all the commands.
   */
  public void printUsage() {
    System.out.println("Usage: java TFsShell");
    System.out.println("       [ls <path>]");
    System.out.println("       [mkdir <path>]");
    System.out.println("       [rm <path>]");
    System.out.println("       [mv <src> <dst>");
    System.out.println("       [copyFromLocal <src> <remoteDst>]");
    System.out.println("       [copyToLocal <src> <localDst>]");
    System.out.println("       [location <path>]");
  }

  /**
   * Main method, starts a new TFsShell
   * @param argv[] Array of arguments given by the user's input from the terminal
   */
  public static void main(String argv[]) throws TException{
    TFsShell shell = new TFsShell();
    System.exit(shell.run(argv));
  }

  /**
   * Method which determines how to handle the user's request, will display usage help to the
   * user if command format is incorrect.
   * @param argv[] Array of arguments given by the user's input from the terminal
   * @return 0 if command is successful, -1 if an error occured
   * @throws TException
   */
  public int run(String argv[]) throws TException {
    if (argv.length == 0) {
      printUsage();
      return -1;
    }

    String cmd = argv[0];
    int exitCode = -1;
    try {
      if (cmd.equals("ls")) {
        exitCode = ls(argv);
      } else if (cmd.equals("mkdir")) {
        exitCode = mkdir(argv);
      } else if (cmd.equals("rm")) {
        exitCode = rm(argv);
      } else if (cmd.equals("mv")) {
        exitCode = rename(argv);
      } else if (cmd.equals("copyFromLocal")) {
        exitCode = copyFromLocal(argv);
      } else if (cmd.equals("copyToLocal")) {
        exitCode = copyToLocal(argv);
      } else if (cmd.equals("location")) {
        exitCode = location(argv);
      } else {
        printUsage();
        return -1;
      }
    } catch (InvalidPathException ipe) {
      System.out.println("Invalid Path: " + ipe.getMessage());
    } catch (FileDoesNotExistException fdne) {
      System.out.println("File Does Not Exist: " + fdne.getMessage());
    } catch (FileAlreadyExistException faee) {
      System.out.println("File Already Exists: " + faee.getMessage());
    } catch (IOException ioe) {
      System.out.println(ioe.getMessage());
    } finally {
    }

    return exitCode;
  }
}
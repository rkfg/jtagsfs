package ru.rkfg.jtagsfs;

import static ru.rkfg.jtagsfs.Consts.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import net.fusejna.DirectoryFiller;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import ru.rkfg.jtagsfs.domain.FileRecord;

public class FSHandlerManager {

    public static class FSHandlerException extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = 7065891672128697332L;

        public FSHandlerException(String string) {
            super(string);
        }

    }

    public static class FSHandlerRuntimeException extends RuntimeException {

        /**
         * 
         */
        private static final long serialVersionUID = 3480870833432919617L;

        public FSHandlerRuntimeException(String message) {
            super(message);
        }
    }

    public static class FSHandlerFileException extends FSHandlerRuntimeException {

        /**
         * 
         */
        private static final long serialVersionUID = 6910554242554795364L;

        public FSHandlerFileException(String message) {
            super(message);
        }

    }

    public static List<String> fileRecordsToNames(List<FileRecord> fileRecords) {
        List<String> result = new LinkedList<String>();
        for (FileRecord fileRecord : fileRecords) {
            result.add(fileRecord.getName());
        }
        return result;
    }

    RootHandler rootHandler = new RootHandler();

    List<FSHandler> handlers = rootHandler.getHandlers();

    HashMap<String, FSHandler> pathHandlerMap = new HashMap<String, FSHandler>();

    public FSHandlerManager() {
        handlers.add(rootHandler);
        for (FSHandler handler : handlers) {
            pathHandlerMap.put(handler.getPrefix(), handler);
        }
    }

    public boolean containsByName(List<FileRecord> fileRecords, String name) {
        for (FileRecord fileRecord : fileRecords) {
            if (fileRecord.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void getattr(String path, StatWrapper stat) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler;
        if (filepath.getName() == null) {
            handler = getHandlerByPath(filepath.up(1));
        } else {
            handler = getHandlerByPath(filepath);
        }
        handler.getattr(filepath.strip(handler.getDepth()), stat);
    }

    public FSHandler getHandlerByPath(Filepath filepath) throws FSHandlerException {
        FSHandler result;
        if (filepath.getPath().length > 0) {
            result = pathHandlerMap.get(filepath.getPath()[0]);
        } else {
            result = pathHandlerMap.get("/");
        }
        if (result == null) {
            throw new FSHandlerException("Handler not found.");
        }
        return result;
    }

    public boolean isContent(String[] pathTags) {
        if (pathTags == null || pathTags.length == 0) {
            return false;
        }
        return pathTags[pathTags.length - 1].equals(ENDOFTAGS);
    }

    public Filepath parseFilePath(String path) {
        String[] pathTags = splitPath(path);
        Filepath result = new Filepath();
        if (pathTags.length > 1 && (pathTags[pathTags.length - 2].equals(ENDOFTAGS) || pathTags[pathTags.length - 2].equals(TAGGEDCONTENT))) {
            result.setName(pathTags[pathTags.length - 1]);
            result.setPath(Arrays.copyOf(pathTags, pathTags.length - 2));
        } else {
            if (pathTags.length > 1 && pathTags[pathTags.length - 1].equals(ENDOFTAGS)) {
                result.setPath(Arrays.copyOf(pathTags, pathTags.length - 1));
                result.setContent(true);
            } else {
                if (pathTags.length > 1 && pathTags[pathTags.length - 1].equals(TAGGEDCONTENT)) {
                    result.setPath(Arrays.copyOf(pathTags, pathTags.length - 1));
                    result.setContent(true);
                    result.setContentWithTags(true);
                } else {
                    result.setPath(pathTags);
                }
            }
        }
        return result;
    }

    public int read(String path, ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
        Filepath filepath = parseFilePath(path);
        try {
            FSHandler handler = getHandlerByPath(filepath);
            return handler.read(strip(filepath, handler), buffer, size, offset);
        } catch (FSHandlerException e) {
            return 0;
        }
    }

    public void readdir(String path, DirectoryFiller filler) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler = getHandlerByPath(filepath);
        filler.add(handler.readdir(strip(filepath, handler)));
    }

    public String[] splitPath(String path) {
        path = path.substring(1);
        if (path.isEmpty()) {
            return new String[0];
        }
        return path.split(File.separator);
    }

    public void mkdir(String path) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler = getHandlerByPath(filepath);
        handler.mkdir(strip(filepath, handler));
    }

    private Filepath strip(Filepath filepath, FSHandler handler) {
        return filepath.strip(handler.getDepth());
    }

    public void rename(String path, String newName) throws FSHandlerException {
        Filepath from = parseFilePath(path);
        Filepath to = parseFilePath(newName);
        FSHandler handlerFrom = getHandlerByPath(from);
        FSHandler handlerTo = getHandlerByPath(to);
        if (handlerFrom == handlerTo) {
            handlerFrom.rename(strip(from, handlerFrom), strip(to, handlerFrom));
        } else {
            throw new FSHandlerException("cross-module renaming not allowed.");
        }
    }

    public void unlink(String path) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler = getHandlerByPath(filepath);
        handler.unlink(strip(filepath, handler));
    }

    public void truncate(String path, long offset) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler = getHandlerByPath(filepath);
        handler.truncate(strip(filepath, handler), offset);
    }

    public void create(String path, FileInfoWrapper info) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler = getHandlerByPath(filepath);
        handler.create(strip(filepath, handler), info);
    }

    public int write(String path, ByteBuffer buffer, long bufSize, long writeOffset, FileInfoWrapper wrapper) {
        Filepath filepath = parseFilePath(path);
        try {
            FSHandler handler = getHandlerByPath(filepath);
            return handler.write(strip(filepath, handler), buffer, bufSize, writeOffset);
        } catch (FSHandlerException e) {
            return 0;
        }
    }

    public void open(String path, FileInfoWrapper info) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler = getHandlerByPath(filepath);
        handler.open(strip(filepath, handler), info);
    }

    public void release(String path, FileInfoWrapper info) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler = getHandlerByPath(filepath);
        handler.release(strip(filepath, handler), info);
    }

    public void rmdir(String path) throws FSHandlerException {
        Filepath filepath = parseFilePath(path);
        FSHandler handler = getHandlerByPath(filepath);
        handler.rmdir(strip(filepath, handler));
    }
}

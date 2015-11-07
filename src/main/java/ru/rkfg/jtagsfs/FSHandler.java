package ru.rkfg.jtagsfs;

import java.nio.ByteBuffer;
import java.util.List;

import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;

public interface FSHandler {

    public String getPrefix();

    public List<String> readdir(Filepath filepath) throws FSHandlerException;

    public void getattr(Filepath filepath, StatWrapper stat) throws FSHandlerException;

    public int getDepth();

    int read(Filepath filepath, ByteBuffer buffer, long size, long offset) throws FSHandlerException;

    void mkdir(Filepath filepath) throws FSHandlerException;

    void rename(Filepath from, Filepath to) throws FSHandlerException;

    void unlink(Filepath filepath) throws FSHandlerException;

    void truncate(Filepath filepath, long offset) throws FSHandlerException;

    public void create(Filepath filepath, FileInfoWrapper info) throws FSHandlerException;

    public int write(Filepath filepath, ByteBuffer buffer, long bufSize, long writeOffset) throws FSHandlerException;

    public void open(Filepath filepath, FileInfoWrapper info) throws FSHandlerException;

    public void release(Filepath filepath, FileInfoWrapper info) throws FSHandlerException;

    public void rmdir(Filepath strip) throws FSHandlerException;

    public void cleanup();
}

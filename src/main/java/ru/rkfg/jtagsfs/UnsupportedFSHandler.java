package ru.rkfg.jtagsfs;

import java.nio.ByteBuffer;
import java.util.Set;

import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;

public abstract class UnsupportedFSHandler implements FSHandler {

    @Override
    public Set<String> readdir(Filepath filepath) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void getattr(Filepath filepath, StatWrapper stat) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public int read(Filepath filepath, ByteBuffer buffer, long size, long offset) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void mkdir(Filepath filepath) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void rename(Filepath from, Filepath to) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void unlink(Filepath filepath) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void truncate(Filepath filepath, long offset) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void create(Filepath filepath, FileInfoWrapper info) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public int write(Filepath filepath, ByteBuffer buffer, long bufSize, long writeOffset) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void open(Filepath filepath, FileInfoWrapper info) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void release(Filepath filepath, FileInfoWrapper info) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

    @Override
    public void rmdir(Filepath strip) throws FSHandlerException {
        throw new FSHandlerException("notsupp");
    }

}

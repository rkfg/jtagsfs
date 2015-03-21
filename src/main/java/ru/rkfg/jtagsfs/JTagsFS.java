package ru.rkfg.jtagsfs;

import static ru.rkfg.jtagsfs.Consts.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.StructTimeBuffer.TimeBufferWrapper;
import net.fusejna.types.TypeMode.ModeWrapper;
import net.fusejna.util.FuseFilesystemAdapterFull;
import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;

public class JTagsFS extends FuseFilesystemAdapterFull {

    FSHandlerManager manager = new FSHandlerManager();

    private static String[] options;

    public static void main(String[] args) throws FuseException {
        if (args.length == 0) {
            System.err.println("Usage: jtagsfs <mountpoint>");
            return;
        }
        String path = args[args.length - 1];
        options = Arrays.copyOf(args, args.length - 1);
        if (path.startsWith("~" + File.separator)) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        HibernateUtil.initSessionFactory("hibernate.cfg.xml");
        new File(STORAGE).mkdirs();
        new JTagsFS().log(false).mount(path);
    }

    @Override
    protected String[] getOptions() {
        return options;
    }

    @Override
    protected String getName() {
        return "jtagsfs";
    }

    @Override
    public int create(String path, ModeWrapper mode, FileInfoWrapper info) {
        try {
            manager.create(path, info);
            return 0;
        } catch (FSHandlerException e) {
            return -ErrorCodes.EACCES();
        }
    }

    @Override
    public int getattr(String path, StatWrapper stat) {
        try {
            stat.uid(getFuseContextUid().longValue());
            stat.gid(getFuseContextGid().longValue());
            manager.getattr(path, stat);
        } catch (FSHandlerException e) {
            return -ErrorCodes.ENOENT();
        }
        return 0;
    }

    @Override
    public int mkdir(String path, ModeWrapper mode) {
        try {
            manager.mkdir(path);
        } catch (FSHandlerException e) {

        }
        return 0;
    }

    @Override
    public int read(String path, ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
        return manager.read(path, buffer, size, offset, info);
    }

    @Override
    public int readdir(String path, DirectoryFiller filler) {
        try {
            manager.readdir(path, filler);
        } catch (FSHandlerException e) {
            return -ErrorCodes.ENOENT();
        }
        return 0;
    }

    @Override
    public int utimens(String path, TimeBufferWrapper wrapper) {
        return 0;
    }

    @Override
    public int write(String path, ByteBuffer buffer, long bufSize, long writeOffset, FileInfoWrapper wrapper) {
        return manager.write(path, buffer, bufSize, writeOffset, wrapper);
    }

    @Override
    public int truncate(String path, long offset) {
        try {
            manager.truncate(path, offset);
        } catch (FSHandlerException e) {

        }
        return 0;
    }

    @Override
    public int rename(String path, String newName) {
        try {
            manager.rename(path, newName);
            return 0;
        } catch (FSHandlerException e) {
            return -ErrorCodes.EACCES();
        }
    }

    @Override
    public int unlink(String path) {
        try {
            manager.unlink(path);
            return 0;
        } catch (FSHandlerException e) {
            return -ErrorCodes.EACCES();
        }
    }

    @Override
    public int open(String path, FileInfoWrapper info) {
        try {
            manager.open(path, info);
            return 0;
        } catch (FSHandlerException e) {
            return 0;
        }
    }

    @Override
    public int release(String path, FileInfoWrapper info) {
        try {
            manager.release(path, info);
            return 0;
        } catch (FSHandlerException e) {
            return 0;
        }
    }

    @Override
    public void afterUnmount(File mountPoint) {
        super.afterUnmount(mountPoint);
        FSHandlerManager.stopTimers();
    }

    @Override
    public int rmdir(String path) {
        try {
            manager.rmdir(path);
        } catch (FSHandlerException e) {
            if (e.getMessage().equals("notfound")) {
                return -ErrorCodes.ENOENT();
            }
            if (e.getMessage().equals("access")) {
                return -ErrorCodes.EACCES();
            }
            return -ErrorCodes.EINVAL();
        }
        return 0;
    }

}

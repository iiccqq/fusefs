package com.example.fuse;

import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

import java.nio.file.Paths;
import java.util.*;

public class MemoryFuse extends FuseStubFS {

  List<String> paths = new ArrayList<>();
  Map<String, String> contents = new HashMap<>();
  Set<String> isDirMap = new HashSet<>();

  @Override
  public int getattr(String path, FileStat stat) {
    int res = 0;

    if (Objects.equals(path, "/")) {
      stat.st_mode.set(FileStat.S_IFDIR | 0755);
      stat.st_nlink.set(paths.size());
    } else {
      if (!paths.contains(path))
        return -ErrorCodes.ENOENT();
      String content = contents.get(path);
      if (isDirMap.contains(path))
        stat.st_mode.set(FileStat.S_IFDIR | 0755);
      else
        stat.st_mode.set(FileStat.S_IFREG | 0444);
      stat.st_nlink.set(1);
      stat.st_size.set(content.getBytes().length);
    }
    return res;
  }

  @Override
  public int mkdir(String path, @mode_t long mode) {
    paths.add(path);
    isDirMap.add(path);
    return 0;
  }

  @Override
  public int rmdir(String path) {
    paths.remove(path);
    isDirMap.remove(path);
    return 0;
  }

  @Override
  public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
    if (!"/".equals(path)) {
      return -ErrorCodes.ENOENT();
    }

    filter.apply(buf, ".", null, 0);
    filter.apply(buf, "..", null, 0);
    for (String p : paths) {
      filter.apply(buf, p.substring(1), null, 0);
    }
    return 0;
  }

  @Override
  public int create(String path, @mode_t long mode, FuseFileInfo fi) {
    paths.add(path);
    contents.put(path, "init");
    return 0;
  }

  @Override
  public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
    final int sz = (int) size;
    final byte[] dest = new byte[sz];
    buf.get(0, dest, 0, sz);
    String text = new String(dest);
    if (contents.containsKey(path)) {
      String oldText = contents.get(path);
      String newText = oldText.substring(0, (int) offset);
      newText += text;
      contents.put(path, newText);
    } else {
      contents.put(path, text);
    }
    return sz;
  }

  @Override
  public int release(String path, FuseFileInfo fi) {
    return 0;
  }

  @Override
  public int open(String path, FuseFileInfo fi) {

    return 0;
  }

  @Override
  public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
    String content = contents.get(path);

    byte[] bytes = content.getBytes();
    int length = bytes.length;
    if (offset < length) {
      if (offset + size > length) {
        size = length - offset;
      }
      buf.put(0, bytes, 0, bytes.length);
    } else {
      size = 0;
    }
    return (int) size;
  }

  @Override
  public int truncate(String path, @off_t long size) {
    String content = contents.get(path);
    if (content != null) {
      content = content.substring(0, (int) size);
      contents.put(path, content);
    }
    return 0;
  }

  @Override
  public int rename(String oldpath, String newpath) {
    for (String path : paths) {
      if (path.equals(oldpath)) {
        paths.remove(path);
        break;
      }
    }
    paths.add(newpath);
    String content = contents.get(oldpath);
    if (content != null) {
      contents.remove(oldpath);
      contents.put(newpath, content);
    } else {
      isDirMap.remove(oldpath);
      isDirMap.add(newpath);
    }

    return 0;
  }

  public static void main(String[] args) {
    MemoryFuse stub = new MemoryFuse();
    try {
      String path = args[0];
      System.out.println("mount path=" + path);
      stub.mount(Paths.get(path), true, true);
    } finally {
      stub.umount();
    }
  }
}
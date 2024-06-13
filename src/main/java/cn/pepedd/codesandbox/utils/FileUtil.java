package cn.pepedd.codesandbox.utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * TODO
 *
 * @author pepedd864
 * @since 2024/6/12
 */
public class FileUtil {
  // 转大写快捷键 CTRL + U
  // 这里将会生成在 target 下的 tmp/code 目录
  private static final String DEFAULT_CODE_PATH = "tmp/code";
  // 最大超时时间
  private static final long TIME_OUT = 5000L;

  /**
   * 用户代码隔离存放
   *
   * @param fileName
   * @param content
   * @return
   */
  public static File createFile(String fileName, String content) {
    Path path = Paths.get(DEFAULT_CODE_PATH + File.separator + UUID.randomUUID() + File.separator + fileName);
    try {
      return cn.hutool.core.io.FileUtil.writeString(content, path.toString(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static String getDefaultCodePath() {
    Path path = Paths.get(DEFAULT_CODE_PATH);
    return path.getParent().toString();
  }
}

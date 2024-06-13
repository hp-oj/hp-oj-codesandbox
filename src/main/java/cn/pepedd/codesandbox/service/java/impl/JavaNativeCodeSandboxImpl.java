package cn.pepedd.codesandbox.service.java.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import cn.pepedd.codesandbox.model.ExecuteCodeRequest;
import cn.pepedd.codesandbox.model.ExecuteCodeResponse;
import cn.pepedd.codesandbox.model.ExecuteMessage;
import cn.pepedd.codesandbox.model.JudgeInfo;
import cn.pepedd.codesandbox.service.CodeSandbox;
import cn.pepedd.codesandbox.service.java.JavaCodeSandboxTemplate;
import cn.pepedd.codesandbox.utils.ProcessUtil;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
@Component
public class JavaNativeCodeSandboxImpl extends JavaCodeSandboxTemplate {
  /**
   * 执行代码
   *
   * @param executeCodeRequest
   * @return
   */
  @Override
  public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
    return super.executeCode(executeCodeRequest);
  }
}

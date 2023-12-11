package org.apache.bigtop.manager.stack.bigtop.v3_3_0.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.apache.bigtop.manager.common.utils.NetUtils;
import org.apache.bigtop.manager.common.utils.shell.DefaultShellResult;
import org.apache.bigtop.manager.common.utils.shell.ShellResult;
import org.apache.bigtop.manager.stack.common.enums.ConfigType;
import org.apache.bigtop.manager.stack.common.utils.LocalSettings;
import org.apache.bigtop.manager.stack.common.utils.linux.LinuxFileUtils;
import org.apache.bigtop.manager.stack.spi.BaseParams;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ZookeeperSetup {

    public static ShellResult config(BaseParams baseParams) {
        log.info("ZookeeperSetup config");
        ZookeeperParams zookeeperParams= (ZookeeperParams) baseParams;

        String confDir = zookeeperParams.confDir();
        String zookeeperUser = zookeeperParams.user();
        String zookeeperGroup = zookeeperParams.group();
        Map<String, Object> zookeeperEnv = zookeeperParams.zookeeperEnv();
        Map<String, Object> zooCfg = zookeeperParams.zooCfg();
        List<String> zkHostList = LocalSettings.hosts("zookeeper_server");

        String logDir = (String) zookeeperEnv.get("logDir");
        String pidDir = (String) zookeeperEnv.get("pidDir");
        String dataDir = (String) zooCfg.get("dataDir");
        LinuxFileUtils.createDirectories(dataDir, zookeeperUser, zookeeperGroup, "rwxr-xr--", true);
        LinuxFileUtils.createDirectories(logDir, zookeeperUser, zookeeperGroup, "rwxr-xr--", true);
        LinuxFileUtils.createDirectories(pidDir, zookeeperUser, zookeeperGroup, "rwxr-xr--", true);

        //针对zkHostList排序，获取当前hostname的index+1
        //server.${host?index+1}=${host}:2888:3888
        zkHostList.sort(String::compareToIgnoreCase);
        StringBuilder zkServerStr = new StringBuilder();
        for (String zkHost : zkHostList) {
            zkServerStr.append(MessageFormat.format("server.{0}={1}:2888:3888", zkHostList.indexOf(zkHost) + 1, zkHost))
                    .append("\n");
        }

        LinuxFileUtils.toFile(ConfigType.CONTENT, MessageFormat.format("{0}/myid", dataDir), zookeeperUser, zookeeperGroup,
                "rw-r--r--", zkHostList.indexOf(NetUtils.getHostname()) + 1 + "");

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("zkServerStr", zkServerStr.toString());
        paramMap.put("securityEnabled", false);
        String templateContent = zooCfg.get("templateContent").toString();
        zooCfg.remove("templateContent");
        Map<String, Object> modelMap = new HashMap<>();
        modelMap.put("model", zooCfg);

        LinuxFileUtils.toFileByTemplate(templateContent, MessageFormat.format("{0}/zoo.cfg", confDir),
                zookeeperUser, zookeeperGroup, "rw-r--r--", modelMap, paramMap);

        Map<String, Object> envMap = new HashMap<>();
        envMap.put("JAVA_HOME", "/usr/local/java");
        envMap.put("ZOOKEEPER_HOME", zookeeperParams.serviceHome());
        envMap.put("ZOO_LOG_DIR", logDir);
        envMap.put("ZOOPIDFILE", pidDir + "/zookeeper_server.pid");
        envMap.put("securityEnabled", false);

        LinuxFileUtils.toFileByTemplate(zookeeperEnv.get("content").toString(), MessageFormat.format("{0}/zookeeper-env.sh", confDir),
                zookeeperUser, zookeeperGroup, "rw-r--r--", envMap);

        return DefaultShellResult.success("ZooKeeper Server Configuration success!");
    }
}

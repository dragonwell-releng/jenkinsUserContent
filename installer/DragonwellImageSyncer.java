import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DragonwellImageSyncer {

    private static final String DRAGONWELL_DOCKERHUB_REPO = "alibabadragonwell/dragonwell";
    private static final String ANOLIS_TARGET_REPO        = "openanolis/dragonwell";
    private static final String DOCKER_LS                 = "./docker-ls";
    private static final String IMAGE_SYNCER              = "./image-syncer";

    private static final String JSON = "images.json";

    private static final Map<String, String> ANOLIS_OS_VERSIONS = new HashMap<String, String>() {  // sha256 -> version
        {
            // docker-ls tag --raw-manifest --parse-history alibabadragonwell/dragonwell:17-anolis
            //
            // requesting manifest . done
            // config:
            //   digest: sha256:edd5aa04f3933c288c1966d2b9940418b7fdb9da1941b3da991fa3adbd13b992
            //   mediaType: application/vnd.docker.container.image.v1+json
            //   size: 4273
            // layers:
            // - digest: sha256:f4bed4d02f435039f242763919de222d8d1f55ed804bcb8b68749f09db761ecf  <----
            //   mediaType: application/vnd.docker.image.rootfs.diff.tar.gzip
            //   size: 7.8643033e+07
            // - digest: sha256:ce21dea34ff880a6bf29a8e0adead837ff1d8066474a16da8c215b5964f02817
            //   mediaType: application/vnd.docker.image.rootfs.diff.tar.gzip
            //   size: 1.400981e+07
            // - digest: sha256:88b6fc814c5c516a0785e5161b189f79eb53bdde2daf5eb8447c996de660bf1e
            //   mediaType: application/vnd.docker.image.rootfs.diff.tar.gzip
            //   size: 1.92926298e+08
            // - digest: sha256:bd9ad50ee5752f310e5590d9eef73e18b9e0474829b266c3c3c50516b9137edb
            //   mediaType: application/vnd.docker.image.rootfs.diff.tar.gzip
            //   size: 1156
            // mediaType: application/vnd.docker.distribution.manifest.v2+json
            // schemaVersion: 2
            put("f4bed4d02f435039f242763919de222d8d1f55ed804bcb8b68749f09db761ecf", "8.6");  // x86_64
            put("3379616b4530f63a367bd30448cd363310c389cfd578f28de6da996b59bfa0e4", "8.6");  // aarch64
            put("f60f4b4a0e3135dc7ed228369c46f7f5e372f1e65346fd079d125ddf82b7c36c", "8.6");  // x86_64, weird. 8-stanard-ga-anolis-x86_64 has this.
        }
    };

    private static final String[] BLACK_LIST = {   // old tag that we do not want to use
            "alinux-dragonwell-",
            "dragonwell-",
            "8-aarch64-anolis-slim",
    };

    public static boolean tagOkay(String tag) {
        if (!tag.contains("anolis")) {
            return false;
        }

        for (String blackPrefix : BLACK_LIST) {
            if (tag.startsWith(blackPrefix)) {
                return false;
            }
        }

        return true;
    }

    private static BufferedReader runProcess(String ... args) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        return new BufferedReader(new InputStreamReader(p.getInputStream()));  // TODO: memory leak.
    }

    public static String anolisVersion(String tag) throws IOException {
        // docker-ls tag --raw-manifest --parse-history alibabadragonwell/dragonwell:17-anolis
        BufferedReader stdInput = runProcess(
                DOCKER_LS,
                "tag",
                "--raw-manifest",
                "--parse-history",
                DRAGONWELL_DOCKERHUB_REPO + ":" + tag
        );

        final String DIGEST_PREFIX = "- digest: sha256:";

        String s;
        while ((s = stdInput.readLine()) != null) {
            if (s.startsWith(DIGEST_PREFIX)) {
                System.out.println(s);  // debug
                // we only match the first digest: the base layer.
                String digest = s.substring(DIGEST_PREFIX.length());
                String anolisVersion = ANOLIS_OS_VERSIONS.get(digest);
                if (anolisVersion == null) {
                    throw new RuntimeException("Unrecognized digest: " + digest + " of " + tag + ". Did you updated OpenAnolis version in your Dragonwell Dockerfile? Please add a digest in ANOLIS_OS_VERSIONS!");
                }
                return anolisVersion;
            }
        }
        throw new RuntimeException("Should not reach here");
    }

    public static List<String[]> getTags() throws IOException {
        List<String[]> list = new ArrayList<>();

        // docker-ls tags alibabadragonwell/dragonwell
        BufferedReader stdInput = runProcess(
                DOCKER_LS,
                "tags",
                DRAGONWELL_DOCKERHUB_REPO
        );

        String s;
        while ((s = stdInput.readLine()) != null) {
            String tag;
            if (s.startsWith("- ") && tagOkay(tag = s.substring("- ".length()))) {
                System.out.println(tag);  // debug
                String anolisVersion = anolisVersion(tag);
                list.add(new String[] { tag, anolisVersion });
            }
        }
        return list;
    }

    public static List<String[]> genDragonwellTagToAnolisTagMapping(List<String[]> dragonwellTags) {
        List<String[]> list = new ArrayList<>(dragonwellTags.size());
        for (String[] pair : dragonwellTags) {
            String tag = pair[0];
            String anolisVersion = pair[1];
            // 8.13.14(-extended-ga)-anolis(-x86_64)(-slim)
            Pattern p1 = Pattern.compile("(\\d+(?:\\.\\d+)*)(-.*)?(-anolis)(-[^-]*)?(-[^-]*)?$");
            Matcher matcher = p1.matcher(tag);
            if (!matcher.find()) {
                System.err.println("[Fatal] " + tag + " didn't match! Skip this one.");
                continue;
            }
            // => [1] 11
            // => [2] -extended-ga (may be null)
            // => [3] -anolis
            // => [4] -aarch64 (may be null)
            // => [5] -slim (may be null)
            for (int i = 1; i <= matcher.groupCount(); i++) {
                System.out.println("=> [" + i + "] " + matcher.group(i));  // debug
            }

            // openanolis/dragonwell:8-extended-8.6-slim-x86_64
            String version = matcher.group(1);
            String detailedVersion = matcher.group(2) == null ? "" : matcher.group(2);
            String arch = matcher.group(4) == null ? "" : matcher.group(4);  // x86_64/aarch64
            String slim = matcher.group(5) == null ? "" : matcher.group(5);

            String newAnolisTag = version + detailedVersion + "-" + anolisVersion + slim + arch;
            System.out.println("[Preview] " + tag + " => " + newAnolisTag);  // debug

            list.add(new String[] { tag, newAnolisTag });
        }
        return list;
    }

    private static void generateImageTaskJSON(List<String[]> mapping) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(System.lineSeparator());
        for (int i = 0; i < mapping.size(); i++) {
            String[] map = mapping.get(i);
            sb.append("  ");
            sb.append("\"").append("registry.hub.docker.com/").append(DRAGONWELL_DOCKERHUB_REPO).append(":").append(map[0]).append("\"");
            sb.append(": ");
            sb.append("\"").append("anolis-registry.cn-zhangjiakou.cr.aliyuncs.com/").append(ANOLIS_TARGET_REPO).append(":").append(map[1]).append("\"");
            if (i != mapping.size()-1) {
                sb.append(",");
            }
            sb.append(System.lineSeparator());
        }
        sb.append("}");

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(JSON))) {
            bw.write(sb.toString());
        }

        System.out.println();
        System.out.println(">>>>>>> IMAGE JSON START >>>>>>>");
        System.out.println(sb);  // debug
        System.out.println(">>>>>>> IMAGE JSON END >>>>>>>");
        System.out.println();
    }

    private static void runImageSyncer() throws IOException {
        BufferedReader stdInput = runProcess(
                IMAGE_SYNCER,
                "--auth=/root/.docker/anolis_sync_auth.json",
                "--images=" + JSON,
                "--retries=3"
        );

        String s;
        while ((s = stdInput.readLine()) != null) {
            System.out.println(s);
        }
    }

    public static void main(String[] args) throws IOException {

        List<String[]> dragonwellTags = getTags();

        List<String[]> mapping = genDragonwellTagToAnolisTagMapping(dragonwellTags);

        generateImageTaskJSON(mapping);

        runImageSyncer();

    }
}

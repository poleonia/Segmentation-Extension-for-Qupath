package qupath.ext.template;

import javafx.application.Platform;
import javafx.concurrent.Task;
import qupath.lib.io.PathIO;
import java.io.InputStream;
import java.io.FileInputStream; 
import java.io.FileOutputStream;  // <-- Add this line
import java.util.ArrayList;
import java.util.List;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.net.HttpURLConnection;
import java.net.URL;


import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.HashSet;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class GLOSMainCommand {

    private static final Logger logger = LoggerFactory.getLogger(GLOSMainCommand.class);
    private final QuPathGUI qupath;
    private String serverURL; 
    private String targetDir;



    public GLOSMainCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }


    public void setFolderPermissions(String folderPath) throws IOException {
    Path path = Paths.get(folderPath);
    
    Set<PosixFilePermission> perms = new HashSet<>();
    perms.add(PosixFilePermission.OWNER_READ);
    perms.add(PosixFilePermission.OWNER_WRITE);
    perms.add(PosixFilePermission.OWNER_EXECUTE);
    perms.add(PosixFilePermission.GROUP_READ);
    perms.add(PosixFilePermission.GROUP_WRITE);
    perms.add(PosixFilePermission.GROUP_EXECUTE);
    perms.add(PosixFilePermission.OTHERS_READ);
    perms.add(PosixFilePermission.OTHERS_WRITE);
    perms.add(PosixFilePermission.OTHERS_EXECUTE);
    
    Files.setPosixFilePermissions(path, perms);
    }

public void downloadFile(String fileUrl, String destinationPath) throws IOException {
    File destinationFile = new File(destinationPath);

    // Check if the file already exists
    if (destinationFile.exists()) {
        System.out.println("File already exists: " + destinationPath);
        return;
    }

    URL url = new URL(fileUrl);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setInstanceFollowRedirects(true);  // Automatically follow redirects
    connection.setRequestMethod("GET");
    connection.connect();

    int status = connection.getResponseCode();
    if (status != HttpURLConnection.HTTP_OK) {
        // Handle redirects (e.g., 303, 302, etc.)
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM ||
            status == HttpURLConnection.HTTP_SEE_OTHER) {
            String newUrl = connection.getHeaderField("Location");
            connection = (HttpURLConnection) new URL(newUrl).openConnection();
            connection.connect();
        } else {
            throw new IOException("Failed to download file: " + connection.getResponseCode());
        }
    }

    try (InputStream inputStream = connection.getInputStream();
         FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

    System.out.println("Downloaded: " + destinationPath);
    setFolderPermissions(destinationPath);

}


// Helper method to parse confirmation token from Google Drive HTML page
private String parseConfirmationToken(HttpURLConnection connection) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("confirm")) {
                // Extract the token from the HTML
                String token = line.split("confirm=")[1].split("&")[0];
                return token;
            }
        }
    }
    return null;
}

    // Method to unzip a .zip file
    public void unzipFile(String zipFilePath, String destDir) throws IOException {
        File dir = new File(destDir);
        if (!dir.exists()) dir.mkdirs();

        try (FileInputStream fis = new FileInputStream(zipFilePath);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(dir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    // Method to download .pth files and Python scripts
    public void downloadResources(String[] pthLinks, String zipFileUrl, String destinationDir) throws IOException {
        // Download all .pth files
        for (String modelUrl : pthLinks) {
            String[] urlParts = modelUrl.split("/");
            String pthFileName = urlParts[urlParts.length - 1];  // Extract the original file name from the URL
            String destinationPath = destinationDir + "/" + pthFileName;
            downloadFile(modelUrl, destinationPath);
        }

    // Ensure the 'zip' directory exists
    File zipDirectory = new File(destinationDir + "/zip");
    if (!zipDirectory.exists()) {
        zipDirectory.mkdirs();  // Create the directory if it doesn't exist
    }

    // Download the Python scripts as a ZIP file from GitHub
    String zipFileDestination = destinationDir + "/zip/python_scripts.zip";
    downloadFile(zipFileUrl, zipFileDestination);

        // Unzip the file after download
        unzipFile(zipFileDestination, destinationDir + "/python_scripts");
    }

    // Method to determine the Desktop directory and create 'QuPath_extension_model' folder
    public String getDesktopDirectory() throws IOException {
        String desktopDir = null;
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // On Windows, the desktop is typically in the user's home directory
            desktopDir = System.getProperty("user.home") + "\\Desktop";
        } else if (os.contains("mac")) {
            // On macOS, the desktop is in the user's home directory
            desktopDir = System.getProperty("user.home") + "/Desktop";
        } else if (os.contains("nix") || os.contains("nux") || os.indexOf("aix") > 0) {
            // On Linux, the desktop is also in the user's home directory
            //desktopDir = "/home/yuej2"+ "/Desktop";
            desktopDir = "/home/yul11"+ "/Desktop";
        } else {
            throw new IOException("Unsupported operating system");
        }

        // Ensure the directory exists
        Files.createDirectories(Paths.get(desktopDir));
        return desktopDir;
    }

    // Submit detection task
    public void submitDetectionTask() {
        try {
            // QuPath directory setup based on OS
            String desktopDir = getDesktopDirectory();  // Get the QuPath directory based on the OS
            String qupathModelDir = desktopDir + "/models_and_pythonfiles";  // Create a models folder in the QuPath directory
            Files.createDirectories(Paths.get(qupathModelDir));

                    // Set folder permissions after creating the directory
            setFolderPermissions(qupathModelDir);

    // // Google Drive direct download links for the .pth files
    // String[] pthLinks = {
    //     "https://drive.google.com/uc?export=download&id=1o9QklDoV9_7BvJCJU_PaEBaTDB7rxFDG",  // model1_best.pth
    //     "https://drive.google.com/uc?export=download&id=1PzMYMAv059pT_HifX3QbLZXWXBsbnBDN",  // model2_best.pth
    //     "https://drive.google.com/uc?export=download&id=1-inIDGP4-zjwSzSpUplbPJEPgoPLFX-Y",  // model3_best.pth
    //     "https://drive.google.com/uc?export=download&id=1L21zS550YAjFmmsBakHDGGMG6ys5ZxDQ",  // model4_best.pth
    //     "https://drive.google.com/uc?export=download&id=1QiPg1XgxIyk0N5tLtANeG45qqqOqEYZG"   // model5_best.pth
    // };

        // Google Drive direct download links for the .pth files
    String[] pthLinks = {
        "https://raw.githubusercontent.com/JLY0814/qupath_wcf_extension/refs/heads/main/WCF/model/model1_best.pth",  // model1_best.pth
        "https://raw.githubusercontent.com/JLY0814/qupath_wcf_extension/refs/heads/main/WCF/model/model2_best.pth",  // model2_best.pth
        "https://raw.githubusercontent.com/JLY0814/qupath_wcf_extension/refs/heads/main/WCF/model/model3_best.pth",  // model3_best.pth
        "https://raw.githubusercontent.com/JLY0814/qupath_wcf_extension/refs/heads/main/WCF/model/model4_best.pth",  // model4_best.pth
        "https://raw.githubusercontent.com/JLY0814/qupath_wcf_extension/refs/heads/main/WCF/model/model5_best.pth"   // model5_best.pth
    };

    // URL for the Python scripts ZIP file
    String zipFileUrl = "https://raw.githubusercontent.com/JLY0814/qupath_wcf_extension/refs/heads/main/WCF/CircleNet_Zip.zip";

            // Download resources (Python scripts and .pth files) to the QuPath models directory
            downloadResources(pthLinks, zipFileUrl, qupathModelDir);

            // Set the directory where the models and Python scripts are located
            String loadModelDir = qupathModelDir;

            // Get WSI path
            String rawPath = qupath.getViewer().getImageData().getServer().getPath();
            String wholeSlideImagePath = rawPath.contains("file:") ? rawPath.split("file:")[1].trim() : rawPath;

            System.out.println("Extracted Whole Slide Image Path: " + wholeSlideImagePath);

            // Generate GeoJSON file path based on WSI name
            String wsiFilePath = new File(wholeSlideImagePath).getName();
            String wsiName;
            if (wsiFilePath.contains("[--series, ")) {

                System.out.println("Multi-Series" );

                wsiName = wsiFilePath.split("\\[")[0];

                if (wsiName.endsWith(".svs")) {
                    wsiName = wsiName + ".geojson";
                } else if (wsiName.endsWith(".scn")) {
                    // Multi-series
                    if (wsiFilePath.contains("series")){
                        String seriesIndex = wsiFilePath.split("\\[--series, ")[1].split("\\]")[0];
                        //int seriesIndex = 1;
                        wsiName = String.format("%s #%s.geojson", wsiName, seriesIndex);

                    }

                } else {
                    logger.warn("Unsupported WSI format for file: " + wholeSlideImagePath);
                    return;
                }

            } else {
                wsiName = wsiFilePath;

                if (wsiName.endsWith(".svs")) {
                    wsiName = wsiName + ".geojson";
                } else if (wsiName.endsWith(".scn")) {
                    wsiName = wsiName + ".geojson";
                } else {
                    logger.warn("Unsupported WSI format for file: " + wholeSlideImagePath);
                    return;
                }
            }



            //if (wsiName.endsWith(".svs")) {
            //    wsiName = wsiName.replace(".svs", ".geojson");
            //} else if (wsiName.endsWith(".scn")) {
            //    wsiName = wsiName.replace(".scn", ".geojson");
            //} else {
            //    logger.warn("Unsupported WSI format for file: " + wholeSlideImagePath);
            //    return;
            //}
            
/////////////////////////////////////change the python interpreter path to your interpreter///////////////////////////////////////////////////////////

            // Step 1: Specify the input directory
            //String inputDir = Paths.get(wholeSlideImagePath).getParent().toString();
            String inputDir = Paths.get(wholeSlideImagePath.split("\\[--series, ")[0]).getParent().toString();
            String outputDir = inputDir + "/output";

            // Step 2: Create input and output directories if they do not exist
            File inputDirectory = new File(inputDir);
            if (!inputDirectory.exists()) {
                ProcessBuilder mkdirInput = new ProcessBuilder("sudo", "mkdir", inputDir);
                Process process = mkdirInput.start();
                process.waitFor();
            }

            File outputDirectory = new File(outputDir);
            if (!outputDirectory.exists()) {
                ProcessBuilder mkdirOutput = new ProcessBuilder("sudo", "mkdir", outputDir);
                Process process = mkdirOutput.start();
                process.waitFor();
            }




            List<String> command = new ArrayList<>();
            //sudo nvidia-docker run -it --rm --shm-size=8g -v $input_dir:/INPUTS/ -v $output_dir:/OUTPUTS hrlblab333/public:PathSeg_test /Pasting/Seg_test.sh

            command.add("sudo"); // Only if sudo is necessary
            //command.add("docker"); // Use "docker" instead of "nvidia-docker" (modern Docker versions handle GPUs with --gpus)
            //command.add("run");
            //command.add("--gpus");
            //command.add("all");
            command.add("nvidia-docker");
            //command.add("images");
            command.add("run");
            //command.add("-it");
            command.add("-i");
            command.add("--rm");
            command.add("--shm-size=8g");
            command.add("-v");
            command.add(inputDir + ":/INPUTS/");
            command.add("-v");
            command.add(outputDir + ":/OUTPUTS/");
            command.add("hrlblab333/public:PathSeg_test");
            command.add("/Pasting/Seg_test.sh");


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            
            // Run the process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            //Map<String, String> env = processBuilder.environment();

//////////////////////////////////////change the environment path here to your circlenet environment//////////////////////////////////////////////////
            
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Capture the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("Python script exited with error code: " + exitCode);
                logger.error("Python script output: " + output.toString());
            } else {
                logger.info("Python script executed successfully.");
                logger.info("Python script output: " + output.toString());
            }

        // 调用生成 GeoJSON 路径的方法
        //String targetDir= qupathModelDir + "/test_only_result";
        String targetDir= outputDir + "/Merge";

        String geojsonDir = generateGeoJsonPath(targetDir);  // targetDir 替换 xml_dir
        // 解析输出结果
        //parsePythonOutput(output.toString(), geojsonDir, wsiName);
        //String baseName = wsiName.contains(".")? wsiName.substring(0, wsiName.lastIndexOf('.')): wsiName;

        int lastDot = wsiName.lastIndexOf('.');
        int secondLastDot = wsiName.lastIndexOf('.', lastDot - 1);

        String baseName;
        if (secondLastDot != -1) {
            baseName = wsiName.substring(0, secondLastDot);
        } else {
            baseName = wsiName;
        }


        //geojsonDir = Paths.get(targetDir, baseName).toString();
        geojsonDir = URLDecoder.decode(Paths.get(targetDir, baseName).toString(), StandardCharsets.UTF_8);
        wsiName = URLDecoder.decode(wsiName, StandardCharsets.UTF_8);
        //wsiName = "merge_result.geojson";
        parsePythonOutput(output.toString(), geojsonDir, wsiName);

        } catch (IOException | InterruptedException e) {
            logger.error("Error during process execution", e);
        }


    }


    // 方法用于生成 GeoJSON 保存目录路径
    private String generateGeoJsonPath(String targetDir) {
        Path targetDirPath = Paths.get(targetDir);
        String baseName = targetDirPath.getFileName().toString();
        String geoJsonDir = targetDirPath.getParent().resolve(baseName + "_geojson").toString();
        return geoJsonDir;
    }

    // 方法用于解析 Python 输出并加载 GeoJSON 文件到 QuPath
    private void parsePythonOutput(String output, String geojsonDir, String wsiName) {
        // 此处可以添加对 Python 输出的解析逻辑
        // 加载生成的 GeoJSON 文件
        loadGeoJsonToQuPath(geojsonDir, wsiName);
    }

    // 方法用于加载 GeoJSON 文件到 QuPath
    private void loadGeoJsonToQuPath(String geojsonDir, String wsiName) {
        File geojsonFile = new File(geojsonDir, wsiName); // 使用生成的 GeoJSON 文件名
        if (geojsonFile.exists()) {
            // 获取文件的输入流
            try (InputStream inputStream = new FileInputStream(geojsonFile)) {
                // 使用 PathIO 提供的 readObjectsFromGeoJSON 方法读取 GeoJSON 文件
                List<PathObject> objects = PathIO.readObjectsFromGeoJSON(inputStream);
                // 将对象添加到 QuPath 的层次结构中
                //qupath.getViewer().getImageData().getHierarchy().addPathObjects(objects);
                qupath.getViewer().getImageData().getHierarchy().addPathObjects(objects);
                logger.info("GeoJSON file loaded successfully: " + geojsonFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to read GeoJSON file: " + geojsonFile.getAbsolutePath(), e);
            }
        } else {
            logger.warn("GeoJSON file not found: " + geojsonFile.getAbsolutePath());
        }
    }
}

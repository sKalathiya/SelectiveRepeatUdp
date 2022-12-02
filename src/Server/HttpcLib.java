package Server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HttpcLib {

    static String getResponse(String request, String dir, boolean verbose) throws IOException {

        String[] words = request.split(" ");
        String path = words[1];
        String data = "";
        String status  = "";
        String code = "";
        if(verbose){
            System.out.println("Processing the Get request");
        }
        try {

            if(path.startsWith("/get?")) {
                path = path.substring(5);
                String[] var = path.split("&");
                int i ;
                for (i = 0; i < var.length; i++)
                {
                    data = data + var[i] + "\n";
                }
            }
            else {
                if (path.equals("/")) {
                    File f = new File(dir);
                    String[] l = f.list();
                    for (String name :
                            l) {
                        data = data + name + System.lineSeparator();

                    }

                } else {
                    boolean check = new File(dir, path).exists();
                    if (!check) {
                        throw new Exception("nf");
                    }
                    boolean isPathGood = checkPath(path,dir);
                    if(isPathGood){
                        String d = Files.readString(Paths.get(dir + path));
                        data = data + d + System.lineSeparator();
                    }else{
                        throw new Exception("nf");
                    }


                }
            }
            status  = "OK";
            code = "200";
        }catch(Exception e)
        {
            if(e.getMessage().equals("nf"))
            {
                status = "File not found";
                code = "404";
            }else{
                status = "Internal Server Error";
                code = "504";
            }
        }
        if(verbose){
            System.out.println("data retreived \nCreating headers");
        }
        String headers = "Httfc/1.0 "+ code + " "+ status + System.lineSeparator() + "Date :" + java.time.LocalDate.now() + System.lineSeparator()
                + "Server : Httpfc/1.0.0" + System.lineSeparator() + "Content-Length: "+ data.length() +System.lineSeparator() +
                "Connection: Closed" + System.lineSeparator() + "Content-type: Application/text" ;
        if(verbose){
            System.out.println("headers created");
        }

        return headers +"\n\n"+data;
    }
    static boolean checkPath(String path,String dir) throws IOException {
        File f = new File(String.valueOf(Paths.get(dir + path)));
        String c = f.getCanonicalPath();
        File f1 = new File(dir);
        String d =f1.getCanonicalPath();
        if(c.startsWith(d)){
            return true;
        }else{
            return false;
        }
    }
     static String postResponse(String req, String directory,boolean verbose) {
        String data = "", status, code;
        if(verbose){
            System.out.println("Processing the Post request");
        }
        try {

            BufferedReader reader = new BufferedReader(new StringReader(req));
            String header = reader.readLine();
            String filename = header.split(" ")[1];
            while (header.length() > 0) {
                header = reader.readLine();
            }

            String bodyLine = reader.readLine();
            while (bodyLine != null) {
                data = data + bodyLine.trim() + "\n";
                bodyLine = reader.readLine();
            }
            data = data.trim();
            if(filename.equals("/")){
                status = "BAD REQUEST";
                code = "400";
                data = "Filename Not Specified";
            } else if (filename.equals("/post")) {
                status = "OK";
                code = "200";
            }
            else {
                Files.write(Path.of(directory , filename), data.getBytes(StandardCharsets.UTF_8));
                status = "OK";
                code = "200";
            }

        } catch (Exception e) {
            status = "INTERNAL SERVER ERROR";
            code = "500";
            data = "Server Error";
        }
        if(verbose){
            System.out.println("data Stored\nCreating headers");
        }
        String headers = "HTTP/1.1 " + code + " " + status + "\n" +
                "Date: " + java.time.LocalDate.now() + "\n" +
                "Content-Type: application/text\n" +
                "Content-Length: " + data.length() + "\n" +
                "Connection: close\n" +
                "Server: httpfs\n" +
                "Access-Control-Allow-Origin: *\n" +
                "Access-Control-Allow-Credentials: true";

        if(verbose){
            System.out.println("headers created");
        }

        return headers + "\n\n" + data;

    }
}

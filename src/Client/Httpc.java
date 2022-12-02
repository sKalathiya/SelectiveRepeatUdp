package Client;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.net.*;

import Util.Response;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.tools.ant.types.Commandline;

public class Httpc{

    public Response sendRequest(int port, String server, String path, String type, boolean verbose, List headers, Object data)
    {
        Response r = null;
        boolean br = false ;
        boolean first  = true;
        String res ="";
        try
        {
            DatagramSocket socket = new DatagramSocket();

            // Follow the HTTP protocol
            String req = type + " "+ path + " HTTPFC/1.0" + System.lineSeparator();
            for (Object header: headers
            ) {
                req = req + header.toString() + System.lineSeparator();
            }

            if (type.equals("POST")){
                req = req + "Content-Length: " + data.toString().length();
                req = req + System.lineSeparator()+"" +
                        "\n";
                req = req + data.toString()+"\n\n";

            }

            System.out.println("Connecting to server");
            int reqport = ReliableClientProtocol.connection(socket, new InetSocketAddress(server, port));
            System.out.println("Connection established, sending request");
            res = ReliableClientProtocol.requestresponse(socket, new InetSocketAddress(server, reqport), req);
            System.out.println("Received util.Response");

            String status="",code="",header="",body="";
//

            // Read data from the server until we finish reading the document
            String lines[] = res.split("\n");

            for( String line:lines )
            {
                if(first){
                    String[] s = line.split(" ");
                    status = s[2];
                    code = s[1];
                    first = false;
                }
                if(!br){
                    if(line.trim().equals("")){
                       br = true;
                    }
                    header = header + line+"\n";
                }
                else{
                    body = body + line+"\n";
                }

            }
            if(verbose)
            {
                 r = new Response(status,code,header,body);

            }
            else {
                r = new Response(status, code, null, body);

            }

        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
        return r;
    }


    public Response getResponse(String cmd ) throws IOException {
        Httpc client = new Httpc();
        String mysrgs[] = Commandline.translateCommandline(cmd);
        Response r ;
        if(mysrgs.length <= 1){

            r = new Response("malurlFunction");

            return r;
        }
        if(!mysrgs[0].equalsIgnoreCase("httpc")){

            r = new Response("Wrong command");
            return r;
        }
        String type = mysrgs[1].toUpperCase();
        if(type.equals("HELP")){
            if(mysrgs.length == 3) {
                r = new Response(null,null,null,client.help(mysrgs[2]));
            }
            else{
                r = new Response(null,null,null,client.help("asda"));

            }
            return r;
        }

        String u = mysrgs[mysrgs.length - 1];
        URL url=new URL(u);
        String path = url.getPath();
        String host = url.getHost();
        int p = url.getPort();
        if(path.equals("/get"))
        {
            path = path + "?" + url.getQuery();
        }
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("verbose", "v"), "Verbose")
                .withOptionalArg()
                .defaultsTo("localhost");
        parser.acceptsAll(Arrays.asList("header", "h"), "headers")
                .withOptionalArg()
                .defaultsTo("localhost");
        parser.acceptsAll(Arrays.asList("data", "d"), "data")
                .withOptionalArg()
                .defaultsTo("localhost");
        parser.acceptsAll(Arrays.asList("file", "f"), "data from file")
                .withOptionalArg()
                .defaultsTo("localhost");
        parser.acceptsAll(Arrays.asList("writeinfile", "o"), "write result in file")
                .withOptionalArg()
                .defaultsTo("localhost");



        OptionSet options = parser.parse(mysrgs);
        List headers = options.valuesOf("h");
        boolean verbose= options.has("v");
        Object data="";
        if(options.has("d")){
            data = options.valueOf("d");
        }
        if(options.has("f")){
            Object name = options.valueOf("f");
            data = Files.readString(Paths.get(name.toString().trim()));
        }
        boolean winFile = options.has("o");
        Object file = "";
        if(winFile){
            file  = options.valueOf("o");
        }
         r = client.sendRequest(p,host, path, type, verbose,headers,data);
        r.setFile(file.toString());
        r.setInFile(winFile);
        return r;

    }

    public String help(String option){
        switch(option.toUpperCase()){
            case "GET":
                return "usage: httpc get [-v] [-h key:value] URL\n\n" +
                        "Get executes a HTTP GET request for a given URL.\n" +
                        "\n\t" +
                        "-v\tPrints the detail of the response such as protocol, status, and headers.\n\t" +
                        "-h key:value\tAssociates headers to HTTP Request with the format 'key:value'.\n";

            case "POST":
                return "usage: httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL\n\n"+
                        "Post executes a HTTP POST request for a given URL with inline data or from " +
                        "file.\n" +
                        "\n\t" +
                        "-v\tPrints the detail of the response such as protocol, status, " +
                        "and headers.\n\t-h key:value\t Associates headers to HTTP Request with the format " +
                        "'key:value'.\n\t-d string\t Associates an inline data to the body HTTP POST request.\n\t-f " +
                        "file\tAssociates the content of a file to the body HTTP POST request\n\n" +
                        "Either [-d] or [-f] can be used but not both.\n";

            default:
                return "httpc is a curl-like application but supports HTTP protocol only.\n" +
                        "Usage:\n\thttpc command [arguments]\n" +
                        "The commands are:\n\tget\texecutes a HTTP GET request and prints the response. " +
                        "\n\tpost\texecutes a HTTP POST request and prints the response. " +
                        "\n\thelp\tprints this screen.\n\n" +
                        "Use \"httpc help [command]\" for more information about a command.";
        }
    }

}

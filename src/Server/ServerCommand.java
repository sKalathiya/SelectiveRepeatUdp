package Server;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.tools.ant.types.Commandline;
import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

public class ServerCommand {


    public static void main(String args[]) throws IOException {

        Scanner sc = new Scanner(System.in);
        String cmd = sc.nextLine();
        parse(cmd);
    }

        public static void parse(String cmd) throws IOException {
            String mysrgs[] = Commandline.translateCommandline(cmd);
            String r;
            if (mysrgs.length <= 1) {

                r = "Wrong Command";

            }
            if (!mysrgs[0].equalsIgnoreCase("httpfs")) {

                r = "Wrong command";

            }

            OptionParser parser = new OptionParser();
            parser.acceptsAll(Arrays.asList("verbose", "v"), "TimeServer hostname");
            parser.acceptsAll(Arrays.asList("port", "p"), "TimeServer hostname")
                    .withOptionalArg()
                    .defaultsTo("8080");
            parser.acceptsAll(Arrays.asList("directory", "d"), "TimeServer data")
                    .withOptionalArg()
                    .defaultsTo(".");

            OptionSet options = parser.parse(mysrgs);
            int port = Integer.parseInt((String) options.valueOf("p"));
            boolean verbose = options.has("v");
            String directory = (String) options.valueOf("d");
            ServerTcpProtocl server = new ServerTcpProtocl(port,directory,verbose);
        }

}

package Client;



import Util.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class CLient {
    public static void main(String[] args) throws IOException {
        String dec = "yes";
        while (dec.equals("yes")) {
            Scanner sc = new Scanner(System.in);
            System.out.println("Enter your command..");
            String cmd = sc.nextLine();
            Httpc c = new Httpc();
            Response r = c.getResponse(cmd);
            if(r.isInFile()){
                Files.write(Paths.get(r.getFile()), r.toString().getBytes(StandardCharsets.UTF_8));
            }
            else {
                System.out.println(r.toString());
            }
            System.out.println("do you want to continue?");
            dec = sc.nextLine();
        }
    }
}

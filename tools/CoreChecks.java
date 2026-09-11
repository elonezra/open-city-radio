import com.opencity.radio.LiveClock;
import com.opencity.radio.SafeZip;
import java.io.*;
import java.nio.file.*;
import java.math.BigInteger;
import java.util.*;
import java.util.zip.*;

public final class CoreChecks {
    static int checks;
    interface Task { void run() throws Exception; }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    static void rejects(Task task) throws Exception { try { task.run(); } catch (IOException | IllegalArgumentException expected) { checks++; return; } throw new AssertionError("Expected rejection"); }
    static byte[] zip(String name, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes)) { z.putNextEntry(new ZipEntry(name)); z.write(content.getBytes()); z.closeEntry(); }
        return bytes.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        long now = 1789128000000L, duration = 700001;
        long first = LiveClock.position(now,duration,"a",300,false,0);
        check(LiveClock.position(now+180000,duration,"a",300,false,0)==(first+180000)%duration,"Live advance after close");
        check(LiveClock.position(now+duration,duration,"a",300,false,0)==first,"Modulo loop");
        check(LiveClock.position(-10,100,"a",0,false,0)==90,"Negative timestamps");
        check(LiveClock.position(1000,100,"a",-30,false,0)==70,"Negative offset");
        rejects(()->LiveClock.position(now,0,"a",0,false,0));
        rejects(()->LiveClock.position(now,-1,"a",0,false,0));
        long seeded=LiveClock.position(now,duration,"a",0,true,0);
        check(seeded==LiveClock.position(now,duration,"a",0,true,0),"Deterministic seed");
        check(LiveClock.position(now+1000,duration,"a",0,true,0)==(seeded+1000)%duration,"No daily-seed drift during day");
        check(seeded!=LiveClock.position(now,duration,"b",0,true,0),"Independent station seeds");
        long max=Long.MAX_VALUE;
        long expected=BigInteger.valueOf(max).subtract(BigInteger.valueOf(Long.MIN_VALUE)).add(BigInteger.valueOf(max)).mod(BigInteger.valueOf(max-4)).longValue();
        check(LiveClock.position(max,max-4,"a",max,false,Long.MIN_VALUE)==expected,"Overflow-safe clock arithmetic");
        File root=Files.createTempDirectory("radio-core-").toFile();
        try {
            check(SafeZip.resolve(root,"audio/demo.wav").toPath().startsWith(root.toPath()),"Valid relative path");
            for (String bad : Arrays.asList("../escape.wav","/absolute.wav","..\\escape.wav","C:/escape.wav","nested/../../escape.wav",".")) rejects(()->SafeZip.resolve(root,bad));
            byte[] valid=zip("audio/test.txt","verified");
            SafeZip.extract(new ByteArrayInputStream(valid),root,n->{});
            check(Files.readString(root.toPath().resolve("audio/test.txt")).equals("verified"),"ZIP extraction");
            rejects(()->SafeZip.extract(new ByteArrayInputStream(zip("../escape.wav","bad")),root,n->{}));
            check(!new File(root.getParentFile(),"escape.wav").exists(),"No write outside staging");
            // The same canonical file must not be overwritten through an alias.
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            try(ZipOutputStream z=new ZipOutputStream(data)) { for(String n:List.of("duplicate.txt","x/../duplicate.txt")){z.putNextEntry(new ZipEntry(n));z.write(1);z.closeEntry();} }
            rejects(()->SafeZip.extract(new ByteArrayInputStream(data.toByteArray()),root,n->{}));
        } finally { try(var paths=Files.walk(root.toPath())) { paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}}); } }
        System.out.println("PASS: " + checks + " core checks against production Java sources");
    }
}

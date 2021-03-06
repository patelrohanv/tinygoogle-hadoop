import java.io.*;
import java.io.File;
import java.io.IOException;
import java.lang.*;
import java.util.*;
import java.util.Scanner;
import java.util.StringTokenizer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.KeyValueTextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class tinyGoogle {

    public static File file = new File(".");
    public static String currDir = file.getAbsolutePath();

    /*
    MAPREDUCE every book in input to ("word^filename, freq")
    */
    public static class frequencyMapper extends Mapper<Object, Text, Text, IntWritable>{

        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();
        private String token;

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            StringTokenizer itr = new StringTokenizer(value.toString());
            while (itr.hasMoreTokens()) {
                token = preProcess(itr.nextToken());
                //token = itr.nextToken();
                String fileName = ((FileSplit) context.getInputSplit()).getPath().getName();
                token = token+"^"+fileName;
                word.set(token);
                context.write(word, one);
            }
        }
        public String preProcess(String s) {
            String stemmed, lower, alpha;                       //
            //PorterStemmer stemmer = new PorterStemmer();        //
            //stemmed = stemmer.stem(s);                          //perform stemming
            //lower = stemmed.toLowerCase();                      //convert to lowercase
            lower = s.toLowerCase();                            //convert to lowercase
            alpha = lower.replaceAll("[^a-zA-Z]", "");          //retain only alphabet chars
            return alpha;
        }
    }

    public static class frequencyReducer extends Reducer<Text,IntWritable,Text,IntWritable> {
        private IntWritable result = new IntWritable();

        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }

    /*
    MAPREDUCE everything into an inverted index
    */
    public static class indexMapper extends Mapper<Text, Text, Text, Text>{
        public void map(Text key, Text value, Context context) throws IOException, InterruptedException {
            StringTokenizer itr = new StringTokenizer(value.toString());
            String fileName = "";
            fileName = itr.nextToken();
            fileName = fileName.substring(0, fileName.length()-4);
            String out = fileName + " " + itr.nextToken();
            Text output = new Text(out);
            context.write(key, output);
        }
    }

    public static class indexReducer extends Reducer<Text,Text,Text,Text> {
        private Text result = new Text();

        public void reduce(Text key, Text value, Context context) throws IOException, InterruptedException {
            String sum = "";
            sum += value.toString();
            sum += "/";
            result.set(sum);
            context.write(key, result);
        }
    }

    /*
    CALL FREQUENCY GENERATING MAPREDUCE JOB
    */
    public static void wordCount(Path inP, Path outP) throws Exception{
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "word count");
        job.setJarByClass(tinyGoogle.class);
        job.setMapperClass(frequencyMapper.class);
        job.setCombinerClass(frequencyReducer.class);
        job.setReducerClass(frequencyReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        FileInputFormat.addInputPath(job, inP);
        FileOutputFormat.setOutputPath(job, outP);
        job.waitForCompletion(false);
    }

    /*
    CALL INVERTED INDEX GENERATING MAPREDUCE JOB
    */
    public static void invertedIndex(Path inP, Path outP) throws Exception{
        Configuration conf = new Configuration();
        conf.set("mapreduce.input.keyvaluelinerecordreader.key.value.separator", "^");
        Job job = Job.getInstance(conf, "word count");
        job.setJarByClass(tinyGoogle.class);
        job.setMapperClass(indexMapper.class);
        job.setCombinerClass(indexReducer.class);
        job.setReducerClass(indexReducer.class);
        job.setInputFormatClass(KeyValueTextInputFormat.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, inP);
        FileOutputFormat.setOutputPath(job, outP);
        job.waitForCompletion(false);
    }

        // BRUTE FORCING IT
    public static class indexPair implements Comparable<indexPair>{
        public String t;
        public int l;

        public indexPair(String t, int l){
            this.t = t;
            this.l = l;
        }

        public String getKey(){
            return t;
        }

        public int getValue(){
            return l;
        }

        public int compareTo(indexPair p){
            if(p.getValue() > l){
                return -1;
            }
            else if(p.getValue() < l){
                return 1;
            }
            else{
                return 0;
            }
        }
    }

    static HashMap<String, LinkedList<indexPair>> hashmap = new HashMap<String, LinkedList<indexPair>>();
    public static void bruteIndex(Path iPath){
        String partR = iPath.toString()+"/part-r-00000";
        try{
            Scanner f = new Scanner(new File(partR));
            while(f.hasNextLine()){
                String line = f.nextLine();

                StringTokenizer itr = new StringTokenizer(line);
                if(itr.countTokens() < 3 ){ continue; }
                int count = 0;
                String term = "";
                String doc = "";
                int freq = -1;
                term = itr.nextToken();
                doc = itr.nextToken().replaceAll("of", " of").replaceAll("by", " by").replaceAll("(.)([A-Z])", "$1 $2");
                freq = Integer.parseInt(itr.nextToken());

                if(!hashmap.containsKey(term)){
                    hashmap.put(term, new LinkedList<indexPair>());
                    hashmap.get(term).add(new indexPair(doc, freq));
                }
                else{
                    hashmap.get(term).add(new indexPair(doc, freq));
                }
            }
        }
        catch(Exception e){
            System.err.print(e + "\n");
        }

    }
    //END BRUTE FORCING IT
    /*
    START CLIENT
    */
    public static void main(String[] args) throws Exception {
        System.out.println("____________________________________________________________________");
        System.out.println("\t\tWelcome to tiny-Google");
        System.out.println("\tBy Salvatore Avena and Rohan Patel");
        System.out.println("____________________________________________________________________");
        int input;
        Scanner kbd = new Scanner(System.in);
        if (!indexed()) {
            while(true) {
                System.out.println("No index found on disk.\nWould you like to generate one now?\n\t1. Yes\n\t2. No");
                input = kbd.nextInt();
                if(input > 2 || input < 1){
                    System.out.println("Not a valid option. Please try again.\n");
                }
                else if (input == 1) {
                    index(args);
                    break;
                }
                else {
                    System.out.println("Ok. Note: No searches will be possible until an index is generated.");
                    System.out.println("____________________________________________________________________");
                    break;
                }
            }
        }
        else {
            while(true) {
                System.out.println("An index already exists on disk.\nWould you like to use the existing index or create a new one?\n\t1. Use the existing index\n\t2. Create a new one");
                input = kbd.nextInt();
                if(input > 2 || input < 1){
                    System.out.println("Not a valid option. Please try again.\n");
                }
                else if (input == 2) {
                    System.out.println("Ok, existing index will be deleted.");
                    removeDirectory(new File(currDir + "/index"));
                    index(args);
                    break;
                }
                else {
                    System.out.println("Ok, existing index will be used.");
                    Path indexOut = new Path(currDir + "/index");
                    bruteIndex(indexOut);
                    System.out.println("____________________________________________________________________");
                    break;
                }
            }
        }

        do{
            System.out.println("Enter an option:\n\t1. Perform a search query \n\t2. Add a document to the Index\n\t3. Generate a New Index from a Directory\n\t4. Quit");
            input = kbd.nextInt();
            if(input > 4 || input < 1){
                System.out.println("Not a valid option.\nPlease try again.");
                continue;
            }
            if (input==1 && !indexed()) {
                System.out.println("No index is present.\nPlease create an index before attempting a search.");
                System.out.println("____________________________________________________________________");
            }
            else if (input == 1) {
                search();
            }
            else if (input == 2) {
                indexFile();
            }
            else if (input == 3) {
                index(args);
            }
        }while(input != 4);
        System.out.println("____________________________________________________________________");
        System.out.println("Goodbye!");
        System.out.println("____________________________________________________________________");
    }

    public static boolean indexed(){
        File[] contents = new File(currDir).listFiles();
        for (File f :contents) {
            if (f.toString().equals(currDir+"/index")) {
                return true;
            }
        }
        return false;
    }

    public static void index(String[] args) throws Exception{
        System.out.println("____________________________________________________________________");
        System.out.println("Creating New Index.");
        System.out.println("____________________________________________________________________");
        Scanner in = new Scanner(System.in);
        System.out.print("Please enter input path:\t");
        String response = in.nextLine();
        Path inPath = new Path (response);
        System.out.print("Please enter output path:\t");
        response = in.nextLine();
        Path outPath = new Path (response);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        System.out.println("\tPlease wait while the index is generated ... ");
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        try {
            wordCount(inPath, outPath);
            Path indexOut = new Path(currDir + "/index");
            invertedIndex(outPath, indexOut);
            bruteIndex(indexOut);
        }
        catch(Exception e){
            System.err.print(e + "\n");
        }
        System.out.println("____________________________________________________________________");
        return;
    }

    public static void search() {
        Scanner in = new Scanner(System.in);
        System.out.print("Please enter a search query:\t");
        String response = in.nextLine();
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        System.out.println("\tPlease wait while the search is completed ... ");
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        String split[] = response.split(" ");
        for(int i = 0; i <split.length; i++) {
            String term = split[i].toLowerCase();
            boolean x = true;
            LinkedList<indexPair> search = new LinkedList<indexPair>();
            try {
                search = (LinkedList<indexPair>)hashmap.get(term).clone();
            }
            catch(Exception e){
                System.out.println("--------------------------------------------------------------------");
                System.out.println("Term \""+term+"\" could not be found in any document.");
                x = false;
            }

            if(x) {
                Collections.sort(search);
                System.out.println("--------------------------------------------------------------------");
                System.out.println("The term \""+term+"\" is found in:");
                while(!search.isEmpty()){
                    indexPair result = search.removeLast();
                    System.out.println("\t" + result.getKey() + "\n\t\tNumber of Appearances: " + result.getValue());
                }
            }
        }
        System.out.println("--------------------------------------------------------------------");
        System.out.println("____________________________________________________________________");
    }

    public static void indexFile() {
        System.out.println("____________________________________________________________________");
        System.out.println("Indexing file.");
        System.out.println("____________________________________________________________________");

        //TODO file indexing functionality
        Scanner in = new Scanner(System.in);
        System.out.print("Please enter the path of the file to be indexed:\t");
        String response = in.nextLine();
        Path inPath = new Path (response);
        System.out.print("Please enter output path:\t");
        response = in.nextLine();
        Path outPath = new Path (response);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        System.out.println("\tPlease wait while the index is updated ... ");
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        try {
            wordCount(inPath, outPath);
            Path indexUpdate = new Path(currDir + "/indexUpdate");
            invertedIndex(outPath, indexUpdate);
            bruteIndex(indexUpdate);
            removeDirectory(new File(indexUpdate.toString()));
        }
        catch(Exception e){
            System.err.print(e + "\n");
        }
        System.out.println("____________________________________________________________________");
    }

    public static boolean removeDirectory(File directory) {
        if (directory == null)
            return false;
        if (!directory.exists())
            return true;
        if (!directory.isDirectory())
            return false;

        String[] list = directory.list();

        if (list != null) {
            for (int i = 0; i < list.length; i++) {
                File entry = new File(directory, list[i]);

                if (entry.isDirectory())
                {
                    if (!removeDirectory(entry))
                    return false;
                }
                else
                {
                    if (!entry.delete())
                    return false;
                }
            }
        }
        return directory.delete();
    }

}

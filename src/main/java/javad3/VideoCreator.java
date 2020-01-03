package javad3;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoCreator {
	
	private String videoBaseDirectory = "video";
	private String framesDirectory = "frames";
	private String framesPrefix = "chart";
	private int screenshotsPerPhantomInstance = 500;
	private int concurrentPhantomInstances = 4;
	private int framerate = 25;
	private String outputFilePath = "output";
	private boolean overwriteOutput = true;
	
	private ArrayList<BufferedReader> phantomReaders;
	private BufferedOutputStream ffmpegContainer;
	
	private D3Object chart;
	
	public VideoCreator(D3Object chart) {
		this.chart = chart;
	}
	
	public void setScreenshotsPerInstance(int screenshotsPerInstance) {
		this.screenshotsPerPhantomInstance = screenshotsPerInstance;
	}
	
	public void setFramerate(int framerate) {
		this.framerate = framerate;
	}
	
	public void setChart(D3Object chart) {
		this.chart = chart;
	}
	
	public void setFramesDirectory(String directory) {
		this.framesDirectory = directory;
	}
	
	public void setFramesPrefix(String prefix) {
		this.framesPrefix = prefix;
	}
	
	public void setOutputFilePath(String path) {
		this.outputFilePath = path;
	}
	
	public void setOverwriteOutput(boolean overwrite) {
		this.overwriteOutput = overwrite;
	}
	
	public void setConcurrentPhantomInstances(int num) {
		this.concurrentPhantomInstances = num;
	}
	
	public void createVideo() throws IOException {
		this.saveHTML();
		int fileCount = this.getFileCount();
		this.bufferFromHTML(fileCount);
	}
	
	private boolean saveHTML() {
		ProcessBuilder processBuilder;
		Process process;
		try {
			processBuilder = new ProcessBuilder();
			processBuilder.directory(new File("./" + videoBaseDirectory));
			processBuilder.command("./" + videoBaseDirectory + "/phantomjs.exe", "saveHTML.js", this.chart.getLocation(), this.framesDirectory + "/" + this.framesPrefix, Integer.toString(this.framerate));
			process = processBuilder.start();
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
		try {
			int test = process.waitFor();
			return test == 0;	
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private int getFileCount() {
		int curr = this.chart.getCountDatapoints() * this.framerate;
		
		File file = Paths.get(this.videoBaseDirectory + "/" + this.framesDirectory + "/" + this.framesPrefix + Integer.toString(curr) + ".html").toFile();
		
		if(file.exists()) {
			while(file.exists()) {
				curr++;
				file = Paths.get(this.videoBaseDirectory + "/" + this.framesDirectory + "/" + this.framesPrefix + Integer.toString(curr) + ".html").toFile();
			}
		} else {
			while(!file.exists()) {
				curr--;
				file = Paths.get(this.videoBaseDirectory + "/" + this.framesDirectory + "/" + this.framesPrefix + Integer.toString(curr) + ".html").toFile();
			}
			curr++;
		}
		
		return curr;
	}
	
	private void bufferFromHTML(int fileCount) throws IOException {
		
		int curr = 0;
		int runs = this.determineRuns(fileCount);
		
		
		if(runs <= 0) {
			return;
		}
		
		this.createFfmpegContainer();
		this.phantomReaders = new ArrayList<>(this.concurrentPhantomInstances);
		
		for(int remainingRuns = runs; remainingRuns > 0; remainingRuns--) {
			if(remainingRuns > 1) {
				for(int i = 0; i < this.concurrentPhantomInstances; i++) {
					int start = curr + i;
					int end = curr + this.concurrentPhantomInstances * (this.screenshotsPerPhantomInstance - 1) + i;
					this.phantomReaders.add(this.createPhantomReader(start, end, this.concurrentPhantomInstances));
				}
				curr = curr + this.concurrentPhantomInstances * this.screenshotsPerPhantomInstance;
			} else {
	
				for(int i = 0; i < this.concurrentPhantomInstances; i++) {
					int start = curr + i;
					int end = fileCount - 1;
					
					if(start > end) {
						break;
					}
					
					this.phantomReaders.add(this.createPhantomReader(start, end, this.concurrentPhantomInstances));
				}
			}
			
			this.feedToFfmpeg();
			this.phantomReaders.clear();
		}
		
		this.ffmpegContainer.close();
	}
	
	private int determineRuns(int fileCount) {
		int runs = 0;
		int remainingFiles = fileCount;
		
		while(remainingFiles > 0) {
			remainingFiles = remainingFiles - this.concurrentPhantomInstances * this.screenshotsPerPhantomInstance;
			runs++;
		}
		
		return runs;
	}
	
	private void feedToFfmpeg() throws IOException {
		boolean atLeastOneBufferHasElements = true;
		ArrayList<BufferedReader> currReaders = this.phantomReaders;
		
		while(atLeastOneBufferHasElements) {
			atLeastOneBufferHasElements = false;
			ArrayList<BufferedReader> nextReaders = new ArrayList<BufferedReader>();
			
			for(BufferedReader reader : currReaders) {
				String line = reader.readLine();
				
				if(line == null) {
					reader.close();
					continue;
				}
				
				atLeastOneBufferHasElements = true;
				try {
					byte[] imageBytes = Base64.getDecoder().decode(line);
					this.ffmpegContainer.write(imageBytes);
					nextReaders.add(reader);
				} catch (Exception e) {
					System.out.println("Test");
				}
				
			}
			
			currReaders = nextReaders;
		}
	}
	
	private void createFfmpegContainer() throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.directory(new File("./" + videoBaseDirectory));
		
		List<String> commands = new ArrayList<>();
		
		commands.add("./" + videoBaseDirectory + "/ffmpeg.exe");
		
		if(this.overwriteOutput) {
			commands.add("-y");
		}
		
		commands.add("-framerate");
		commands.add(Integer.toString(this.framerate));
		commands.add("-i");
		commands.add("-");
		commands.add("-r");
		commands.add(Integer.toString(this.framerate));
		commands.add(this.outputFilePath + ".mp4");
		
		processBuilder.command(commands);
		
		Process process = processBuilder.start();

		OutputStream ffmpegInput = process.getOutputStream();
		
		InputStream err = process.getErrorStream();
		BufferedReader errBuf = new BufferedReader(new InputStreamReader(err));
		
		Runnable errStreamReader = () -> {
			String line;
			try {
				line = errBuf.readLine();
				while(line != null) {
					line = errBuf.readLine();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		};
		
		ExecutorService executor = Executors.newFixedThreadPool(10);
		executor.execute(errStreamReader);

		
		BufferedOutputStream ffmpegInputBuffered = new BufferedOutputStream(ffmpegInput);
		
		this.ffmpegContainer = ffmpegInputBuffered;
	}
	
	private BufferedReader createPhantomReader(int start, int end, int incrementBy) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.directory(new File("./" + videoBaseDirectory));
		processBuilder.command("./" + videoBaseDirectory + "/phantomjs.exe", 
				"makeScreenshot.js", 
				this.framesDirectory + "/" + this.framesPrefix, 
				Integer.toString(start), 
				Integer.toString(end),
				Integer.toString(incrementBy));
		
		processBuilder.redirectErrorStream(true);
		Process process = processBuilder.start();
		
		InputStream phantomOutput = process.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(phantomOutput));
		
		return reader;
	}
	
}
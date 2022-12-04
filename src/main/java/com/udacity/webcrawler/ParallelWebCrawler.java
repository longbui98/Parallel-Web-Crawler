package com.udacity.webcrawler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on
 * a {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
	private final Clock clock;
	private final Duration timeout;
	private final int popularWordCount;
	private final ForkJoinPool pool;
	private final List<Pattern> ignoredUrls;
	private final int maxDepth;
	private final PageParserFactory parserFactory;

	@Inject
	ParallelWebCrawler(Clock clock, @Timeout Duration timeout, @PopularWordCount int popularWordCount,
			@TargetParallelism int threadCount,  @IgnoredUrls List<Pattern> ignoredUrls,
			@MaxDepth int maxDepth, PageParserFactory parserFactory) {
		this.clock = clock;
		this.timeout = timeout;
		this.popularWordCount = popularWordCount;
		this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
		this.ignoredUrls = ignoredUrls;
		this.maxDepth = maxDepth;
		this.parserFactory = parserFactory;
	}

	@Inject
	PageParserFactory pageParserFactory;

	@Override
	public CrawlResult crawl(List<String> startingUrls) {
		Instant deadline = clock.instant().plus(timeout);
		ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
		ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();
		for (String url : startingUrls) {
			pool.invoke(new ParallelTask(url, deadline, maxDepth, counts, visitedUrls));
		}

		if (counts.isEmpty()) {
			return new CrawlResult.Builder().setWordCounts(counts).setUrlsVisited(visitedUrls.size()).build();
		}

		return new CrawlResult.Builder().setWordCounts(WordCounts.sort(counts, popularWordCount))
				.setUrlsVisited(visitedUrls.size()).build();
	}

	public final class ParallelTask extends RecursiveTask<Boolean> {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private String url;
		private Instant deadline;
		private int maxDepth;
		private ConcurrentHashMap<String, Integer> counts;
		private ConcurrentSkipListSet<String> visitedUrls;

		public ParallelTask(String url, Instant deadline,
				int maxDepth, ConcurrentHashMap<String,Integer> counts, ConcurrentSkipListSet<String> visitedUrls) {
			this.url = url;
			this.deadline = deadline;
			this.maxDepth = maxDepth;
			this.counts = counts;
			this.visitedUrls = visitedUrls;
		}

		@Override
		protected Boolean compute() {
			if(maxDepth == 0 || clock.instant().isAfter(deadline)) {
				return false;
			}
			
			for(Pattern pattern : ignoredUrls) {
				if(pattern.matcher(url).matches()) return false;
			}
			
			if(!visitedUrls.add(url)) return false;
			
			PageParser.Result result = parserFactory.get(url).parse();
			
			for(Map.Entry<String, Integer> entry: result.getWordCounts().entrySet()) {
				if(counts.containsKey(entry.getKey())) {
					counts.put(entry.getKey(), counts.get(entry.getKey()) + entry.getValue());
				}else {
					counts.put(entry.getKey(), entry.getValue());
				}
			}
			
			List<ParallelTask> subTasks = new ArrayList<>();
			
			for (String link : result.getLinks()) {
				subTasks.add(new ParallelTask(link, deadline, maxDepth - 1, counts, visitedUrls));
			}
			invokeAll(subTasks);
			return true;
		}
		
	}

	@Override
	public int getMaxParallelism() {
		return Runtime.getRuntime().availableProcessors();
	}
}

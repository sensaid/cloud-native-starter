package com.ibm.webapi.business;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import com.ibm.webapi.data.ArticlesDataAccess;
import com.ibm.webapi.data.AuthorsDataAccess;
import com.ibm.webapi.data.NoConnectivity;
import org.eclipse.microprofile.faulttolerance.Fallback;
import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class Service {

	private List<Article> lastReadArticles;

	// v1 requests five articles
	// v2 requests ten articles
	private int requestedAmount = 5; 

	public Service() {
	}
	
	@Inject
	ArticlesDataAccess dataAccessArticles;
	
	@Inject
    AuthorsDataAccess dataAccessAuthors;

	public CoreArticle addArticle(String title, String url, String author) throws NoDataAccess, InvalidArticle {
		if (title == null)
			throw new InvalidArticle();

		long id = new java.util.Date().getTime();
		String idAsString = String.valueOf(id);

		if (url == null)
			url = "Unknown";
		if (author == null)
			author = "Unknown";

		CoreArticle article = new CoreArticle();
		article.title = title;
		article.id = idAsString;
		article.url = url;
		article.author = author;

		try {
			dataAccessArticles.addArticle(article);
			return article;
		} catch (NoConnectivity e) {
			e.printStackTrace();
			throw new NoDataAccess(e);
		}
	}

	@Fallback(fallbackMethod = "fallbackNoArticlesService")
	public List<Article> getArticles() throws NoDataAccess {
		List<Article> articles = new ArrayList<Article>();	
		List<CoreArticle> coreArticles = new ArrayList<CoreArticle>();		
				
		try {
			coreArticles = dataAccessArticles.getArticles(requestedAmount);							
		} catch (NoConnectivity e) {
			System.err.println("com.ibm.webapi.business.getArticles: Cannot connect to articles service");
			throw new NoDataAccess(e);
		}		
		
		articles = this.createArticleList(coreArticles);
				
		return articles;
	}

	private List<Article> createArticleList(List<CoreArticle> coreArticles) {
		List<Article> articles = new ArrayList<Article>();
		for (int index = 0; index < coreArticles.size(); index++) {
			CoreArticle coreArticle = coreArticles.get(index);
			Article article = new Article();
			article.id = coreArticle.id;
			article.title = coreArticle.title;
			article.url = coreArticle.url;
			article.authorName = coreArticle.author;
			try {
				Author author = dataAccessAuthors.getAuthor(coreArticle.author);
				article.authorBlog = author.blog;
				article.authorTwitter = author.twitter;
			} catch (NoConnectivity e) {	
				System.err.println("com.ibm.webapi.business.getArticles: Cannot connect to authors service");
				article.authorBlog = "";
				article.authorTwitter = "";
			} catch (NonexistentAuthor e) {	
				article.authorBlog = "";
				article.authorTwitter = "";
			}
			articles.add(article);
		}
		lastReadArticles = articles;
		return articles;
	}

	public List<Article> fallbackNoArticlesService() {
		System.err.println("com.ibm.webapi.business.fallbackNoArticlesService: Cannot connect to articles service");
		if (lastReadArticles == null) lastReadArticles = new ArrayList<Article>();
		return lastReadArticles;
	}

	public CompletionStage<List<Article>> getArticlesReactive() {
		CompletableFuture<List<Article>> future = new CompletableFuture<>();
		
		dataAccessArticles.getArticlesReactive(requestedAmount).thenApplyAsync(coreArticles -> {
			List<Article> articles = this.createArticleList(coreArticles);			
			return articles;
		}).whenComplete((articles, throwable) -> {
			future.complete(articles);          
		});

        return future;
	}
}

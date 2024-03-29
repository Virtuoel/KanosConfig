package virtuoel.kanos_config.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.MixinService;

public abstract class ConfigHandler<S> implements Supplier<S>
{
	private final String namespace;
	private final ILogger logger;
	private final Path configFile;
	private final Supplier<S> defaultConfig;
	private S cachedConfig = null;
	private Collection<Runnable> invalidationListeners = new ArrayList<>();
	
	public ConfigHandler(String namespace, Path path, Supplier<S> defaultConfig)
	{
		this.namespace = namespace;
		this.logger = MixinService.getService().getLogger(namespace);
		this.configFile = path;
		this.defaultConfig = defaultConfig;
	}
	
	public String getNamespace()
	{
		return namespace;
	}
	
	public synchronized void onConfigChanged()
	{
		if (cachedConfig != null)
		{
			save(cachedConfig);
			
			invalidate();
		}
	}
	
	public synchronized void invalidate()
	{
		cachedConfig = null;
		
		for (final Runnable listener : invalidationListeners)
		{
			listener.run();
		}
	}
	
	public void addInvalidationListener(Runnable listener)
	{
		invalidationListeners.add(listener);
	}
	
	@Override
	public S get()
	{
		return cachedConfig != null ? cachedConfig : (cachedConfig = load());
	}
	
	public synchronized S load()
	{
		S configData = null;
		try
		{
			Files.createDirectories(configFile.getParent());
			if (Files.exists(configFile))
			{
				try (final Stream<String> lines = Files.lines(configFile))
				{
					configData = readConfig(lines);
				}
				catch (Exception e)
				{
					final Path configBackup = configFile.getParent().resolve(configFile.getFileName().toString() + ".bak");
					logger.warn("Failed to read config for {}. A backup is being made at \"{}\". Resetting to default config.", namespace, configBackup.toString());
					logger.catching(e);
					
					try
					{
						Files.copy(configFile, configBackup, StandardCopyOption.REPLACE_EXISTING);
					}
					catch (IOException e2)
					{
						logger.warn("Failed to backup old config for {}.", namespace);
						throw e2;
					}
				}
			}
		}
		catch (IOException e)
		{
			logger.catching(e);
		}
		
		final S defaultData = defaultConfig.get();
		if (!Objects.equals(configData, defaultData))
		{
			final S mergedData = configData == null ? defaultData : mergeConfigs(configData, defaultData);
			if (!Objects.equals(configData, mergedData))
			{
				configData = mergedData;
				save(configData);
			}
		}
		
		return configData;
	}
	
	public void save()
	{
		save(get());
	}
	
	public void save(S configData)
	{
		try
		{
			Files.write(configFile, writeConfig(configData));
		}
		catch (IOException e)
		{
			logger.warn("Failed to write config for {}:", namespace);
			logger.catching(e);
		}
	}
	
	protected abstract S readConfig(Stream<String> lines);
	
	protected abstract Iterable<? extends CharSequence> writeConfig(S configData) throws IOException;
	
	protected abstract S mergeConfigs(S configData, S defaultData);
}

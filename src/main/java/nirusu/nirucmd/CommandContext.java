package nirusu.nirucmd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.Channel.Type;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Permission;

/**
 * This class represents the current command context (Context (Guild, Private,
 * ...), Event, Message etc.) and contains useful methods to interact with the
 * bot.
 */
public class CommandContext {
    private static final int FILE_SIZE_MAX = 8388119;
    private static final String EMTPY_STRING = "";
    private List<String> args;
    private String key;
    private final Type context;
    private final MessageCreateEvent event;

    /**
     * Creates a new {@link CommandContext}.
     *
     * @param event represents the current event
     */
    public CommandContext(@Nonnull MessageCreateEvent event) {
        this.event = event;
        context = event.getMessage().getChannel().blockOptional().map(Channel::getType).orElse(Channel.Type.UNKNOWN);
    }

    public void setArgs(@Nonnull List<String> args) {
        this.args = Collections.unmodifiableList(args);
    }

    public void setArgsAndKey(String[] userInput, String key, boolean removeFirstEntry) {
        if (removeFirstEntry) {
            this.args = Collections.unmodifiableList(Stream.of(userInput).skip(1).collect(Collectors.toList()));
        } else {
            this.args = Arrays.asList(userInput);
        }
        this.key = key;
    }

    public String getKey() {
        if (key != null) {
            return key;
        }
        return getArgs().map(argsList -> {
            if (argsList.isEmpty()) {
                return EMTPY_STRING;
            }
            return argsList.get(0);
        }).orElse(EMTPY_STRING);
    }

    public void setKey(@Nonnull String key) {
        this.key = key;
    }

    /**
     * Shortcut for a reply with a given message @param message
     */
    public Optional<Message> reply(@Nonnull String message) {
        return event.getMessage().getChannel().blockOptional().map(m -> {
            return m.createMessage(spec -> spec.setContent(message).setMessageReference(event.getMessage().getId())).blockOptional();
        }).orElse(Optional.empty());
    }

    public Optional<Message> send(@Nonnull String message) {
        return event.getMessage().getChannel().blockOptional().map(m -> {
            return m.createMessage(message).blockOptional();
        }).orElse(Optional.empty());
    }

    /**
     * Checks if the current context is equal to @param context
     *
     * @return true if {@link #context} equals @param context
     */
    public boolean isContext(Type context) {
        return this.context.equals(context);
    }

    public Optional<User> getAuthor() {
        return event.getMessage().getAuthor();
    }

    /**
     * !!! ARGS IS NOT MODIFIABLE !!! {@link Collections#unmodifiableList}
     *
     * @return list of all arguments
     */
    public Optional<List<String>> getArgs() {
        return Optional.ofNullable(args);
    }

    public List<String> getArgsOrEmpty() {
        return args == null ? Collections.emptyList() : Collections.unmodifiableList(args);
    }

    public String getUserInput() {
        if (args == null) {
            return EMTPY_STRING;
        }
        return args.stream().collect(Collectors.joining(" "));
    }

    public boolean isPrivate() {
        return isContext(Type.DM);
    }

    public boolean isGuild() {
        return isContext(Type.GUILD_NEWS) || isContext(Type.GUILD_STORE) || isContext(Type.GUILD_TEXT);
    }

    public MessageCreateEvent getEvent() {
        return this.event;
    }

    public Optional<Guild> getGuild() {
        return event.getGuild().blockOptional();
    }

    /**
     * Checks if the author has the permission @param p
     * 
     * @return if contex is not guild return false or user doesnt have the
     *         permission
     */
    public boolean hasGuildPermission(Permission p) {
        return getMember().flatMap(member -> member.getBasePermissions().blockOptional())
                .map(perms -> perms.contains(p)).orElse(false);
    }

    public Optional<Member> getMember() {
        return getGuild()
                .flatMap(guild -> getAuthor().flatMap(user -> guild.getMemberById(user.getId()).blockOptional()));
    }

    public Optional<Message> sendFile(File f) {
        return getChannel().flatMap(ch -> ch.createMessage(mes -> {
            try {
                mes.addFile(f.getName(), new FileInputStream(f));
            } catch (FileNotFoundException e) {
                CommandDispatcher.getLogger().error(e.getMessage(), e);
            }
        }).blockOptional());
    }

    public long getMaxFileSize() {
        return FILE_SIZE_MAX;
    }

    public Optional<MessageChannel> getChannel() {
        return event.getMessage().getChannel().blockOptional();
    }

    public Optional<User> getSelf() {
        return event.getClient().getSelf().blockOptional();
    }

    public Optional<VoiceState> getSelfVoiceState() {
        return getSelfMember().flatMap(self -> self.getVoiceState().blockOptional());
    }

    public Optional<Member> getSelfMember() {
        return getSelf()
                .flatMap(self -> getGuild().flatMap(guild -> guild.getMemberById(self.getId()).blockOptional()));
    }

    public Optional<VoiceState> getAuthorVoiceState() {
        return getMember().flatMap(member -> member.getVoiceState().blockOptional());
    }

    public List<User> getMentionedUsers() {
        return event.getMessage().getUserMentions().collectList().blockOptional().orElse(new ArrayList<>());
    }

    public Optional<Message> createEmbed(Consumer<? super EmbedCreateSpec> spec) {
        return getChannel().map(ch -> ch.createEmbed(spec).blockOptional()).orElse(Optional.empty());
    }
}

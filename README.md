jtagsfs
=======

This is a hierarchic tag filesystem implemented in Java.

Usage
=====

First, you need to create an empty directory for the database and storage. Put the mount script and the .jar file there.
Create another empty directory where your filesystem should be mounted to. Then launch ```jtagsfs /path/to/mountpoint``` 
to mount it. You'll have 3 directories inside, ```control```, ```stat``` and ```tags```. Ignore the ```stat``` directory
as it was done for testing and isn't really used for anything. Create your tags in the ```control``` directory (as directories).
If you want some tags only be visible inside other tags, create them hierarchically. For example, you may have tags
like ```HD```, ```BD``` or ```DVD``` that are only applicable for the ```video``` tag and there's no reason to have them
visible in ```pictures``` tag. You then create the ```video``` tag, cd to it and create those ```HD```, ```BD``` or ```DVD```
tags inside.

The ```tags``` directory is where you put your files to and where you search for them.
For example, you want to tag a movie with tags ```video```, ```HD```, ```thriller``` and ```sci-fi```. You cd to 
```/path/to/mountpoint/tags/video/HD/thriller/sci-fi/@``` (the order isn't important) and copy your file there.
The ```@``` directory marks the end of the tags set. You can find more examples at the [Tagsistant](http://www.tagsistant.net/)
website as I mostly derived syntax from it. Though Tagsistant appeared rather poor for me and had issues with stability
so I wrote my own FS. Maybe Tagsistant has matured since and will suit you better, try it.

By default tags are concatenated with boolean AND so when you search for a file you specify a set of tags that it should
have. If you want OR relation, use the ```+``` directory that's visible inside any tag. You may also exclude some of the
tags with ```_``` (but it's not well tested and should be placed after all other tags). Most of the time the default
behavior will be just what you need.

To change tags just move the file to another tag path. If you remove the file, it's removed from the storage forever,
not just from this tag or set of tags! If you want to remove one or several tags, move the file to the path that doesn't
contain these tags.

There's also a special ```@@``` directory which shows internal file IDs and all tags the file has. It's useful to check
if a file is tagged correctly. You may move files from and to this directory just as fine as to/from ```@```. Don't try
to change tags by renaming files here, the only part that matters is the actual filename. Tags are shown just for
information and changing them would lead to renaming a file to itself.

Issues
======

You may get some strange results cd'ing to non-existing tag-directories. It's not checked well. Also, if you don't see
a tag because it's inside another tag, that doesn't mean you can't cd into it. You can and you'll see the results of
searching by this combination of tags (should be empty though), hierarchic nature is only implemented for convenience
to organize tags as their number may grow a lot. You may very well store various media types inside one FS and you probably
don't want to see tags like ```rock``` or ```electronic``` inside ```movies``` (it's your choice in the end). Hence nesting.

The storage distributes files among 1000 of directories internally so the underlying filesystem shouldn't be stressed.
For example, storing a million of files would only store 1000 files per directory and it's pretty acceptable in terms of
disk search time. If that would ever be an issue, with little changes the storage could be converted to two or more levels so it's
scalable enough. The database is powered by H2 which is quite fast for the task. Hibernate is used because I'm not familiar
with JDBC. It doesn't create much overhead as visualvm says though.

I have ~1700 pictures in it and it works fast and without issues for more than a year. YMMV.

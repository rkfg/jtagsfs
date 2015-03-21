jtagsfs
=======

This is a hierarchic tag filesystem implemented in Java.

Usage
=====

First, you need to create an empty directory for the database and storage. Put the mount script and the .jar file there.
Create another empty directory where your filesystem should be mounted to. Then launch ```jtagsfs /path/to/mountpoint``` 
to mount it. You'll have 2 directories inside, ```control``` and ```tags```. Create your tags in the ```control``` directory (as directories), alternatively you may create them in the ```tags``` directory.
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

You may move, rename and delete tags in the ```control``` directory. If a tag has files on it you won't be able to delete such tag.
Please don't try to delete tags inside the ```tags``` directory (yes, this may sound counterintuitive) as file managers don't know
anything about semantic file systems and may recurse much deeper than you want them to. In fact, an attempt of removing a tag (with rm -rf) in the ```tags``` directory would lead to an infinite recursion via the ```+``` relation and may easily remove all of your files inside the storage. There's nothing I can do to prevent it, unfortunately, so just a warning. You may though rename and move (change
hierarchics) tags around in the ```tags``` without the need to go to ```control```.

Issues
======

The storage distributes files among 1000 of directories internally so the underlying filesystem shouldn't be stressed.
For example, storing a million of files would only store 1000 files per directory and it's pretty acceptable in terms of
disk search time. If that would ever be an issue, with little changes the storage could be converted to two or more levels so it's
scalable enough. The database is powered by H2 which is quite fast for the task. Hibernate is used because I'm not familiar
with JDBC. It doesn't create much overhead as visualvm says though.

I have ~1700 pictures in it and it works fast and without issues for more than a year. YMMV.

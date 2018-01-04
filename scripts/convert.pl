#!/usr/bin/perl -w
#
# Simplistic script to convert some marcXml records into the codex format Json
# Not (at all) bibliographically correct, just enough to get some data into
# mod-codex-mock.
#
# Assumes we have yaz-marcdump installed
#
# When converting multiple files
# Command line arguments
my $infile = $ARGV[0] || die "Need an input file name (and maybe a record number)\n";
my $recno = $ARGV[1] || "1";

my @sources = ("local", "kb", "mock");
my $basename = `basename $infile .xml`;
chomp($basename);
my $outfile = $basename . ".json";
print "Converting $infile to $outfile\n";
open (my $of, ">$outfile") or die $!;

my $cmd = "yaz-marcdump -i marcxml $infile";
open(my $fh, '-|', $cmd) or die $!;

my ($title, $alt, $auth, $pub, $puby, $isbn);
while(<$fh>) {
  chomp();
  #print "$_\n";
  $title = trim($1) if (! $title && /^245.*\$a([^\$]+)/);
  $alt = trim($1) if (! $alt && /^245.*\$b([^\$]+)/);
  $auth = trim($1) if (! $auth && /^100.*\$a([^\$]+)/);
  $pub = trim($1) if (! $pub && /^260.*\$b([^\$]+)/);
  $puby = trim($1) if (! $puby && /^260.*\$c([^\$]+)/);
  $isbn = trim($1) if (! $isbn && /^020.*\$a([^\$]+)/);
  if (/^\.*$/) { # empty line separates records
    $pub = join(", ", $pub, $puby);
    my $id = "77777777-7777-7777-7777-000000" . sprintf("%06d", $recno++);
    my $src = "kb"; # MODCXMOCK-13   $sources[$recno % 3];
    my $type = "books";
    if ( $title ) {
      my $json = "{ \"title\":\"$title\" ";
      $json .= ", \"altTitle\":\"$alt\" " if ($alt);
      $json .= ", \"contributor\": [  { \"type\":\"author\", "
                . " \"name\":\"$auth\" } ] " if ($auth);
      $json .= ", \"publisher\":\"$pub\" " if ($pub);
      $json .= ", \"identifier\": [  { \"type\":\"isbn\", "
                . " \"value\":\"$isbn\" } ] " if ($isbn);
      $json .= ", \"type\":\"$type\" ";
      $json .= ", \"source\":\"$src\" }";
      print $of "$id| $json\n";
    }
    ($title, $alt, $auth, $pub, $puby,$isbn) = ("","","","","","");
  }
}
close($of) or die $!;
close($fh) or die $!;
$recno--;
print "Last record converted was number $recno\n\n";


# Helper to trim leading and trailing punctuation and whitespace
sub trim {
  my $x = shift;
  my $orig = $x;
  $x =~ s/^\W*//;
  $x =~ s/\W*$//;
  #print "  trim: '$orig' '$x' \n";
  return $x;
}
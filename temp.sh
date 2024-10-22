 if [ "$(git diff --submodule=log)" != "" ]; then
            echo "Submodule has changes"
            echo "changed=yes" >> $GITHUB_OUTPUT
          else
            echo "No changes in submodule"
            echo "changed=no" >> $GITHUB_OUTPUT
          fi
